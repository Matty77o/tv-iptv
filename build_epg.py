import gzip
import urllib.request
import xml.etree.ElementTree as ET

SOURCES = {
    "uk": "https://epgshare01.online/epgshare01/epg_ripper_UK1.xml.gz",
    "tr": "https://epgshare01.online/epgshare01/epg_ripper_TR3.xml.gz",
    "us": "https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz",
    "plex": "https://epgshare01.online/epgshare01/epg_ripper_PLEX1.xml.gz",
}

DUCKTV_SOURCE = "https://epg.pw/api/epg.xml?channel_id=465350"

# Exact source EPG IDs -> the IDs you want TiviMate to use
CHANNELS = {
    # English / Kids
    "CBeebies.HD.uk": "CBeebies",
    "BabyFirst.TV.us2": "BabyFirst",

    # Extra Kids
    "MOONBUG.KIDS.TV.tr": "Moonbug Kids",
    "plex.tv.BABY.SHARK.TV.plex": "Baby Shark TV",
    "PBS.Kids.Stream.us2": "PBS KIDS",

    # Turkish / Kids
    "TRT.ÇOCUK.HD.tr": "TRT Çocuk",
    "TRT.ÇOCUK.tr": "TRT Çocuk",
    "MİNİKA.ÇOCUK.tr": "Minika Çocuk",

    # Turkish TV
    "STAR.TV.HD.tr": "Star TV",
    "STAR.TV.tr": "Star TV",
    "NOW.tr": "NOW",
    "ATV.HD.tr": "ATV",
    "ATV.tr": "ATV",
    "SHOW.TV.HD.tr": "Show TV",
    "SHOW.TV.tr": "Show TV",
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

        new_channel = ET.Element(
            "channel",
            {"id": output_id}
        )

        display = ET.SubElement(new_channel, "display-name")
        display.text = output_id

        icon = channel.find("icon")
        if icon is not None and icon.get("src"):
            ET.SubElement(
                new_channel,
                "icon",
                {"src": icon.get("src")}
            )

        output.append(new_channel)
        added_channels.add(output_id)

        print(f"MATCHED CHANNEL: {source_id} -> {output_id}")

    # Add programmes
    for programme in root.findall("programme"):
        source_id = programme.get("channel")

        if source_id not in CHANNELS:
            continue

        output_id = CHANNELS[source_id]

        new_programme = ET.Element(
            "programme",
            dict(programme.attrib)
        )

        new_programme.set("channel", output_id)

        for child in programme:
            new_programme.append(child)

        output.append(new_programme)
        programme_count += 1

# Add English Duck TV EPG
try:
    root = download(DUCKTV_SOURCE)

    duck_channel_ids = {
        channel.get("id")
        for channel in root.findall("channel")
        if channel.get("id")
    }

    print(f"Duck TV: {len(duck_channel_ids)} channel IDs loaded")

    # Add Duck TV channel
    if duck_channel_ids and "Duck TV" not in added_channels:
        new_channel = ET.Element(
            "channel",
            {"id": "Duck TV"}
        )

        display = ET.SubElement(new_channel, "display-name")
        display.text = "Duck TV"

        # Copy logo from source if available
        source_channel = root.find("channel")
        if source_channel is not None:
            icon = source_channel.find("icon")

            if icon is not None and icon.get("src"):
                ET.SubElement(
                    new_channel,
                    "icon",
                    {"src": icon.get("src")}
                )

        output.append(new_channel)
        added_channels.add("Duck TV")

        print("MATCHED CHANNEL: English Duck TV -> Duck TV")

    # Add Duck TV programmes
    duck_programmes = 0

    for programme in root.findall("programme"):
        source_id = programme.get("channel")

        if source_id not in duck_channel_ids:
            continue

        new_programme = ET.Element(
            "programme",
            dict(programme.attrib)
        )

        new_programme.set("channel", "Duck TV")

        for child in programme:
            new_programme.append(child)

        output.append(new_programme)
        programme_count += 1
        duck_programmes += 1

    print(f"Duck TV programmes added: {duck_programmes}")

except Exception as e:
    print(f"FAILED Duck TV: {e}")


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
}

missing = wanted_output - added_channels

if missing:
    print()
    print("MISSING:")
    for channel in sorted(missing):
        print(f" - {channel}")
