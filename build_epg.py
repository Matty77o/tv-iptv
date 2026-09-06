import gzip
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

SOURCES = [
    "https://epgshare01.online/epgshare01/epg_ripper_UK1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_TR1.xml.gz",
]

# Exact output IDs we want TiviMate to see.
WANTED = {
    "BabyFirst": [
        "babyfirst",
        "baby first",
    ],
    "CBeebies": [
        "cbeebies",
        "cbeebies hd",
    ],
    "TRT Çocuk": [
        "trt çocuk",
        "trt cocuk",
        "trt çocuk hd",
        "trt cocuk hd",
    ],
    "Minika Çocuk": [
        "minika çocuk",
        "minika cocuk",
        "minika çocuk hd",
        "minika cocuk hd",
    ],
    "Star TV": [
        "star tv",
        "star tv hd",
        "star",
    ],
    "NOW": [
        "now",
        "now tv",
        "now hd",
        "fox",
        "fox hd",
    ],
    "ATV": [
        "atv",
        "atv hd",
    ],
    "Show TV": [
        "show tv",
        "show tv hd",
        "show",
    ],
}


def normalise(text):
    if not text:
        return ""

    return (
        text.lower()
        .strip()
        .replace("İ", "i")
        .replace("I", "i")
        .replace("ş", "s")
        .replace("Ş", "s")
        .replace("ç", "c")
        .replace("Ç", "c")
        .replace("ğ", "g")
        .replace("Ğ", "g")
        .replace("ü", "u")
        .replace("Ü", "u")
        .replace("ö", "o")
        .replace("Ö", "o")
    )


NORMALISED_WANTED = {
    output_name: [normalise(alias) for alias in aliases]
    for output_name, aliases in WANTED.items()
}


def download(url):
    print(f"Downloading {url}")

    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Accept-Encoding": "gzip",
        },
    )

    with urllib.request.urlopen(req, timeout=120) as response:
        data = response.read()

    # EPGShare files are gzip-compressed.
    try:
        data = gzip.decompress(data)
    except gzip.BadGzipFile:
        pass

    return ET.fromstring(data)


def find_match(channel):
    names = []

    channel_id = channel.get("id")
    if channel_id:
        names.append(channel_id)

    for display_name in channel.findall("display-name"):
        if display_name.text:
            names.append(display_name.text)

    normalised_names = [normalise(name) for name in names]

    # First try exact matches.
    for output_name, aliases in NORMALISED_WANTED.items():
        for name in normalised_names:
            if name in aliases:
                return output_name

    # Then slightly looser matches for HD/etc suffixes.
    for output_name, aliases in NORMALISED_WANTED.items():
        for name in normalised_names:
            for alias in aliases:
                if name.startswith(alias + " "):
                    return output_name

    return None


def programme_is_current(programme):
    """
    Keep programmes that have not already finished.
    Also prevents old/stale guide entries from surviving.
    """
    stop = programme.get("stop")

    if not stop:
        return True

    # XMLTV date normally looks like:
    # 20260906143000 +0100
    try:
        dt = datetime.strptime(stop[:14], "%Y%m%d%H%M%S")
        dt = dt.replace(tzinfo=timezone.utc)

        # Allow some recently-finished programmes so the guide
        # doesn't look empty around the current programme boundary.
        now = datetime.now(timezone.utc)

        return dt >= now
    except Exception:
        return True


output = ET.Element(
    "tv",
    {
        "generator-info-name": "Matty77o Custom EPG",
    },
)

channel_mapping = {}
added_channels = set()

for source in SOURCES:
    try:
        root = download(source)
    except Exception as e:
        print(f"FAILED to download {source}: {e}")
        continue

    print(f"Loaded source with {len(root.findall('channel'))} channels")

    source_mapping = {}

    # Find wanted channels.
    for channel in root.findall("channel"):
        old_id = channel.get("id")
        matched_name = find_match(channel)

        if not old_id or not matched_name:
            continue

        source_mapping[old_id] = matched_name
        channel_mapping[old_id] = matched_name

        if matched_name not in added_channels:
            new_channel = ET.Element(
                "channel",
                {"id": matched_name},
            )

            display = ET.SubElement(new_channel, "display-name")
            display.text = matched_name

            icon = channel.find("icon")
            if icon is not None:
                icon_src = icon.get("src")

                if icon_src:
                    ET.SubElement(
                        new_channel,
                        "icon",
                        {"src": icon_src},
                    )

            output.append(new_channel)
            added_channels.add(matched_name)

            print(f"MATCHED: {matched_name} <- {old_id}")

    # Add programmes for channels found in THIS source.
    programme_count = 0

    for programme in root.findall("programme"):
        old_channel = programme.get("channel")

        if old_channel not in source_mapping:
            continue

        if not programme_is_current(programme):
            continue

        new_programme = ET.Element(
            "programme",
            dict(programme.attrib),
        )

        new_programme.set(
            "channel",
            source_mapping[old_channel],
        )

        # Copy all title/desc/category/etc.
        for child in programme:
            new_programme.append(child)

        output.append(new_programme)
        programme_count += 1

    print(f"Added {programme_count} programmes from {source}")


ET.indent(output, space="  ")

tree = ET.ElementTree(output)

tree.write(
    "guide.xml",
    encoding="utf-8",
    xml_declaration=True,
)

print()
print("Generated guide.xml")
print("Channels found:")

for channel in sorted(added_channels):
    print(f" - {channel}")

missing = set(WANTED.keys()) - added_channels

if missing:
    print()
    print("WARNING - no EPG source found for:")
    for channel in sorted(missing):
        print(f" - {channel}")
