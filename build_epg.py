import gzip
import urllib.request
import xml.etree.ElementTree as ET

SOURCES = [
    "https://raw.githubusercontent.com/RNIB-MediaAndCulture/Freeview-EPG_AD-filter/master/epg.xml",
    "https://epgshare01.online/epgshare01/epg_ripper_TR1.xml.gz",
]

WANTED = {
    "babyfirst": "BabyFirst",
    "cbeebies": "CBeebies",
    "trt çocuk": "TRT Çocuk",
    "trt cocuk": "TRT Çocuk",
    "minika çocuk": "Minika Çocuk",
    "minika cocuk": "Minika Çocuk",
    "star tv": "Star TV",
    "now": "NOW",
    "now tv": "NOW",
    "atv": "ATV",
    "show tv": "Show TV",
}

def download(url):
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "Mozilla/5.0"}
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()

    if url.endswith(".gz"):
        data = gzip.decompress(data)

    return ET.fromstring(data)


output = ET.Element("tv")
channel_map = {}

for source in SOURCES:
    try:
        root = download(source)
    except Exception as e:
        print(f"Failed source {source}: {e}")
        continue

    for channel in root.findall("channel"):
        old_id = channel.get("id")
        names = [
            n.text.strip()
            for n in channel.findall("display-name")
            if n.text
        ]

        matched_name = None

        for name in names:
            cleaned = name.lower().strip()

            for key, desired in WANTED.items():
                if cleaned == key or key in cleaned:
                    matched_name = desired
                    break

            if matched_name:
                break

        if matched_name:
            new_id = matched_name

            if new_id not in channel_map.values():
                new_channel = ET.Element(
                    "channel",
                    {"id": new_id}
                )

                display = ET.SubElement(new_channel, "display-name")
                display.text = matched_name

                icon = channel.find("icon")
                if icon is not None:
                    new_channel.append(icon)

                output.append(new_channel)

            channel_map[old_id] = new_id

    for programme in root.findall("programme"):
        old_channel = programme.get("channel")

        if old_channel in channel_map:
            programme.set("channel", channel_map[old_channel])
            output.append(programme)

ET.indent(output, space="  ")

tree = ET.ElementTree(output)
tree.write(
    "guide.xml",
    encoding="utf-8",
    xml_declaration=True
)

print("Generated guide.xml")
print("Matched:", channel_map)
