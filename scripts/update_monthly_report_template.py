from __future__ import annotations

import argparse
import copy
import hashlib
import re
import zipfile
from pathlib import Path

from lxml import etree

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
W14_NS = "http://schemas.microsoft.com/office/word/2010/wordml"
NS = {"w": W_NS}
W = f"{{{W_NS}}}"
W14 = f"{{{W14_NS}}}"


def paragraph_text(element: etree._Element) -> str:
    return "".join(element.xpath(".//w:t/text()", namespaces=NS))


def set_paragraph_text(paragraph: etree._Element, text: str) -> None:
    text_nodes = paragraph.xpath(".//w:t", namespaces=NS)
    if not text_nodes:
        run = paragraph.find(f"{W}r")
        if run is None:
            run = etree.SubElement(paragraph, f"{W}r")
        text_node = etree.SubElement(run, f"{W}t")
        text_nodes = [text_node]

    text_nodes[0].text = text
    if text.startswith(" ") or text.endswith(" "):
        text_nodes[0].set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
    for text_node in text_nodes[1:]:
        text_node.text = ""


def find_paragraph(body: etree._Element, predicate) -> etree._Element:
    for child in body:
        if child.tag == f"{W}p" and predicate(paragraph_text(child)):
            return child
    raise RuntimeError("Required template paragraph was not found")


def remove_attachment_section(body: etree._Element) -> None:
    children = list(body)
    start = next(
        index
        for index, child in enumerate(children)
        if child.tag == f"{W}p" and paragraph_text(child).strip() == "附件1"
    )
    end = next(
        index
        for index, child in enumerate(children)
        if index > start
        and child.tag == f"{W}p"
        and paragraph_text(child).strip().startswith("报：")
    )
    for child in children[start:end]:
        body.remove(child)


def update_prompt_references(body: etree._Element) -> None:
    for paragraph in body.findall(f"{W}p"):
        text = paragraph_text(paragraph)
        updated = text.replace("，并与附件进度统计表保持一致。", "。")
        updated = updated.replace("，并与附件进度统计表保持一致", "")
        updated = updated.replace("。详见附件1。", "。")
        updated = updated.replace("详见附件1。", "")
        if updated != text:
            set_paragraph_text(paragraph, updated)


def update_issue_slot(body: etree._Element) -> None:
    pattern = "\uff08\\d{4}\u5e74\u7b2c\\d+\u671f\uff0c\u603b\u7b2c\\d+\u671f\uff09"
    issue = find_paragraph(body, lambda text: re.search(pattern, text))
    set_paragraph_text(
        issue,
        "\uff08{{REPORT_YEAR}}\u5e74\u7b2c{{ISSUE_NO}}\u671f\uff0c\u603b\u7b2c{{TOTAL_ISSUE_NO}}\u671f\uff09",
    )


def add_pioneer_slots(body: etree._Element) -> None:
    slot_source = find_paragraph(body, lambda text: "{{SECTION_CASE}}" in text)
    prompts = [
        paragraph
        for paragraph in body.findall(f"{W}p")
        if paragraph_text(paragraph).strip().startswith("【预期内容】")
    ]
    placeholders = [
        "{{SECTION_PIONEER_OVERALL}}",
        "{{SECTION_PIONEER_INDICATORS}}",
        "{{SECTION_PIONEER_SUPPORT}}",
        "{{SECTION_PIONEER_EXPERIENCE}}",
    ]
    if len(prompts) != len(placeholders):
        raise RuntimeError(
            f"Expected {len(placeholders)} pioneer prompts, found {len(prompts)}"
        )

    for prompt, placeholder in zip(prompts, placeholders):
        cloned = copy.deepcopy(slot_source)
        cloned.attrib.pop(f"{W14}paraId", None)
        cloned.attrib.pop(f"{W14}textId", None)
        set_paragraph_text(cloned, placeholder)
        body.insert(body.index(prompt) + 1, cloned)


def patch_document_xml(xml_bytes: bytes) -> bytes:
    parser = etree.XMLParser(remove_blank_text=False, resolve_entities=False)
    root = etree.fromstring(xml_bytes, parser)
    body = root.find(f".//{W}body")
    if body is None:
        raise RuntimeError("word/document.xml has no w:body")

    remove_attachment_section(body)
    update_prompt_references(body)
    update_issue_slot(body)
    add_pioneer_slots(body)

    return etree.tostring(
        root,
        encoding="UTF-8",
        xml_declaration=True,
        standalone=True,
    )


def rewrite_docx(reference: Path, output: Path) -> None:
    if reference.resolve() == output.resolve():
        raise ValueError("Reference and output paths must be different")

    with zipfile.ZipFile(reference, "r") as source:
        infos = source.infolist()
        parts = {info.filename: source.read(info.filename) for info in infos}

    parts["word/document.xml"] = patch_document_xml(parts["word/document.xml"])

    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w") as target:
        for info in infos:
            target.writestr(info, parts[info.filename])


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("reference", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    before = sha256(args.reference)
    rewrite_docx(args.reference, args.output)
    after = sha256(args.reference)
    if before != after:
        raise RuntimeError("Reference template changed during editing")

    print(f"reference_sha256={before}")
    print(f"output_sha256={sha256(args.output)}")


if __name__ == "__main__":
    main()
