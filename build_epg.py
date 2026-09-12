import gzip
import html
import re
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

SOURCES = {
    "uk": "https://epgshare01.online/epgshare01/epg_ripper_UK1.xml.gz",
    "tr": "https://epgshare01.online/epgshare01/epg_ripper_TR3.xml.gz",
    "us": "https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz",
    "plex": "https://epgshare01.online/epgshare01/epg_ripper_PLEX1.xml.gz",
}

DUCKTV_DAILY_URL = "https://com.com.tr/tv-rehberi/duck-tv-hd/{date}"
DUCKTV_TZ = timezone(timedelta(hours=3))
# Exact source EPG IDs -> the IDs you want TiviMate to use
CHANNELS = {
    # English / Kids
    "CBeebies.HD.uk": "CBeebies",
    "BabyFirst.TV.us2": "BabyFirst",

    # Extra Kids
    "MOONBUG.KIDS.TV.tr": "Moonbug Kids",
    "plex.tv.BABY.SHARK.TV.plex": "Baby Shark TV",
    "PBS.Kids.Stream.us2": "PBS KIDS",
    "plex.tv.Kidoodle.TV.plex": "Kidoodle TV",

    # Turkish / Kids
    "TRT.ÇOCUK.HD.tr": "TRT Çocuk",
    "MİNİKA.ÇOCUK.tr": "Minika Çocuk",

    # Turkish TV
    "STAR.TV.HD.tr": "Star TV",
    "NOW.tr": "NOW",
    "ATV.HD.tr": "ATV",
    "SHOW.TV.HD.tr": "Show TV",
    "TRT1.HD.tr": "TRT 1",
    "KANAL.D.HD.tr": "Kanal D",
}


def download(url):
    print(f"Downloading: {url}")
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "Mozilla/5.0"}
    )
    with urllib.request.urlopen(req, timeout=120) as response:
        data = response.read()
    try:
        data = gzip.decompress(data)
    except gzip.BadGzipFile:
        pass
    return ET.fromstring(data)


output = ET.Element(
    "tv",
    {"generator-info-name": "Matty77o Custom EPG"}
)

added_channels = set()
matched_source_ids = set()
programme_count = 0

for source_name, source_url in SOURCES.items():
    try:
        root = download(source_url)
    except Exception as e:
        print(f"FAILED {source_name}: {e}")
        continue

    print(f"{source_name}: {len(root.findall('channel'))} channels loaded")

    # Add channel definitions
    for channel in root.findall("channel"):
        source_id = channel.get("id")
        if source_id not in CHANNELS:
            continue

        output_id = CHANNELS[source_id]
        matched_source_ids.add(source_id)
        if output_id in added_channels:
            continue

        new_channel = ET.Element("channel", {"id": output_id})
        display = ET.SubElement(new_channel, "display-name")
        display.text = output_id
        icon = channel.find("icon")
        if icon is not None and icon.get("src"):
            ET.SubElement(new_channel, "icon", {"src": icon.get("src")})

        output.append(new_channel)
        added_channels.add(output_id)
        print(f"MATCHED CHANNEL: {source_id} -> {output_id}")

    # Add programmes
    for programme in root.findall("programme"):
        source_id = programme.get("channel")
        if source_id not in CHANNELS:
            continue

        output_id = CHANNELS[source_id]
        new_programme = ET.Element("programme", dict(programme.attrib))
        new_programme.set("channel", output_id)
        for child in programme:
            new_programme.append(child)
        output.append(new_programme)
        programme_count += 1

# Add English Duck TV EPG
#
# epg.pw is no longer used.  Duck TV's Turkish schedule page currently
# publishes the programme names themselves in English, which is exactly what
# we want in Umay TV.  The surrounding website is Turkish, but only the
# HH:MM -> English programme title -> HH:MM rows are imported.
#
# Keep the channel definition even if the schedule website is temporarily
# unavailable so Duck TV never disappears from guide.xml.
if "Duck TV" not in added_channels:
    new_channel = ET.Element("channel", {"id": "Duck TV"})
    display = ET.SubElement(new_channel, "display-name", {"lang": "en"})
    display.text = "Duck TV"
    ET.SubElement(new_channel, "icon", {"src": "https://epg.ovh/logo/Duck+TV.png"})
    output.append(new_channel)
    added_channels.add("Duck TV")
    print("ADDED CHANNEL: Duck TV")


def download_text(url):
    print(f"Downloading: {url}")
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/152 Safari/537.36",
            "Accept-Language": "en-GB,en;q=0.9,tr;q=0.5",
        },
    )
    with urllib.request.urlopen(req, timeout=120) as response:
        raw = response.read()
    charset = response.headers.get_content_charset() or "utf-8"
    return raw.decode(charset, errors="replace")


def duck_schedule_rows(page_html):
    # Convert the page to readable text and extract rows of the form:
    #   00:00 Albert Explains 00:04
    # The site contains other times in navigation/weather widgets, so after
    # extraction we select the longest continuous chain where each item's end
    # time equals the following item's start time.  That reliably isolates the
    # TV schedule without depending on fragile CSS classes.
    text = re.sub(r"(?is)<script.*?</script>|<style.*?</style>", " ", page_html)
    text = re.sub(r"(?s)<[^>]+>", " ", text)
    text = html.unescape(text)
    text = re.sub(r"\s+", " ", text)

    pattern = re.compile(
        r"(?<!\d)([0-2]\d:[0-5]\d)\s+(.{1,120}?)\s+([0-2]\d:[0-5]\d)(?!\d)"
    )
    candidates = []
    for match in pattern.finditer(text):
        start_time, title, stop_time = match.groups()
        title = re.sub(r"\s+", " ", title).strip(" -|•\t\r\n")
        if not title or len(title) > 100:
            continue
        # Reject obvious site chrome accidentally caught between clock values.
        lowered = title.lower()
        if any(x in lowered for x in (
            "namaz", "imsakiye", "hava durumu", "nöbetçi", "yayın akışı",
            "program bugün", "program yarın", "istanbul", "sabah", "öğle",
        )):
            continue
        candidates.append((start_time, title, stop_time))

    if not candidates:
        return []

    # Find the longest adjacent schedule chain.  A genuine Duck TV day contains
    # hundreds of consecutive short programmes, whereas unrelated page clocks
    # form only tiny fragments.
    best = []
    current = []
    for item in candidates:
        if current and current[-1][2] != item[0]:
            if len(current) > len(best):
                best = current
            current = []
        current.append(item)
    if len(current) > len(best):
        best = current

    # If markup changes and continuity is lost, don't silently import site
    # chrome.  A real Duck TV daily schedule should contain many entries.
    return best if len(best) >= 20 else []


def xmltv_time(day, hhmm, rollover=False):
    hour, minute = map(int, hhmm.split(":"))
    target_day = day + timedelta(days=1 if rollover else 0)
    dt = datetime(
        target_day.year, target_day.month, target_day.day,
        hour, minute, tzinfo=DUCKTV_TZ,
    )
    return dt.strftime("%Y%m%d%H%M%S %z")


duck_programmes = 0
# Pull a full week so the app's multi-day guide keeps working.
# Use Turkey's date because the source page is a Turkish Duck TV schedule.
today_tr = datetime.now(DUCKTV_TZ).date()
for day_offset in range(7):
    day = today_tr + timedelta(days=day_offset)
    url = DUCKTV_DAILY_URL.format(date=day.strftime("%d-%m-%Y"))
    try:
        page = download_text(url)
        rows = duck_schedule_rows(page)
        if not rows:
            print(f"WARNING: Duck TV {day}: no reliable English schedule rows found")
            continue

        print(f"Duck TV {day}: {len(rows)} English schedule rows found")
        previous_start_minutes = None
        day_rollover = 0
        for start_time, title, stop_time in rows:
            sh, sm = map(int, start_time.split(":"))
            eh, em = map(int, stop_time.split(":"))
            start_minutes = sh * 60 + sm
            stop_minutes = eh * 60 + em

            # If the scraped daily page runs beyond midnight, move subsequent
            # entries onto the next calendar day rather than producing negative
            # durations.
            if previous_start_minutes is not None and start_minutes < previous_start_minutes - 600:
                day_rollover += 1
            previous_start_minutes = start_minutes

            start_day = day + timedelta(days=day_rollover)
            stop_day = start_day
            if stop_minutes <= start_minutes:
                stop_day += timedelta(days=1)

            start_dt = datetime(start_day.year, start_day.month, start_day.day, sh, sm, tzinfo=DUCKTV_TZ)
            stop_dt = datetime(stop_day.year, stop_day.month, stop_day.day, eh, em, tzinfo=DUCKTV_TZ)

            programme = ET.Element(
                "programme",
                {
                    "start": start_dt.strftime("%Y%m%d%H%M%S %z"),
                    "stop": stop_dt.strftime("%Y%m%d%H%M%S %z"),
                    "channel": "Duck TV",
                },
            )
            title_el = ET.SubElement(programme, "title", {"lang": "en"})
            title_el.text = title
            category_el = ET.SubElement(programme, "category", {"lang": "en"})
            category_el.text = "Kids"
            output.append(programme)
            programme_count += 1
            duck_programmes += 1

    except Exception as e:
        print(f"FAILED Duck TV schedule for {day}: {e}")

print(f"Duck TV English programmes added: {duck_programmes}")
if duck_programmes == 0:
    print("WARNING: Duck TV channel kept, but no schedule data could be imported")

ET.indent(output, space="  ")
ET.ElementTree(output).write(
    "guide.xml",
    encoding="utf-8",
    xml_declaration=True
)

print()
print("Generated guide.xml")
print(f"Programmes added: {programme_count}")
print()
print("Channels generated:")
for channel in sorted(added_channels):
    print(f" - {channel}")

wanted_output = {
    "BabyFirst",
    "CBeebies",
    "PBS KIDS",
    "TRT Çocuk",
    "Minika Çocuk",
    "Star TV",
    "NOW",
    "ATV",
    "Show TV",
    "Moonbug Kids",
    "Baby Shark TV",
    "Duck TV",
    "Kidoodle TV",
    "TRT 1",
    "Kanal D",
}

missing = wanted_output - added_channels
if missing:
    print()
    print("MISSING:")
    for channel in sorted(missing):
        print(f" - {channel}")
