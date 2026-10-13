"""Conservative CNXML prose extraction for licensed reference-pack inputs.

Keep section titles with their own prose. Do not treat removed figures, exercises,
or inline bibliographic footnotes as prose. Callers own source/license validation.
"""
import re
import xml.etree.ElementTree as ET


def local_name(element):
    return element.tag.rsplit('}', 1)[-1]


EXCLUDED = {'figure', 'media', 'image', 'caption', 'exercise', 'solution',
            'equation', 'footnote'}


def prose(element):
    if local_name(element) in EXCLUDED:
        return ''
    if local_name(element) == 'newline':
        return '\n'
    value = element.text or ''
    for child in element:
        value += prose(child) + (child.tail or '')
    if local_name(element) in {'para', 'item', 'entry', 'row', 'list', 'table'}:
        return '\n\n' + value + '\n\n'
    return value


def sections(raw):
    """Return source-order (heading path, prose) units without duplicating children."""
    root = ET.fromstring(raw)
    title = next((c for c in root if local_name(c) == 'title'), None)
    content = next((c for c in root if local_name(c) == 'content'), None)
    if title is None or content is None:
        raise ValueError('Expected CNXML title and content')
    output = []

    def walk(container, headings):
        pending = []

        def flush():
            text = ''.join(pending)
            text = re.sub(r'\n[ \t]*\n(?:[ \t]*\n)+', '\n\n', text).strip()
            if text:
                output.append((tuple(headings), text))
            pending.clear()

        for child in container:
            tag = local_name(child)
            if tag == 'section':
                flush()
                heading = next((c for c in child if local_name(c) == 'title'), None)
                path = headings + ([prose(heading).strip()] if heading is not None else [])
                walk(child, path)
            elif tag != 'title':
                pending.append(prose(child))
        flush()

    walk(content, [prose(title).strip()])
    return output


def source_record(label, fields):
    """No builder-authored scientific cautions in the source evidence channel."""
    if not label.strip() or any(not isinstance(k, str) or not isinstance(v, str)
                                for k, v in fields.items()):
        raise ValueError('Expected a dataset label and original string fields')
    return 'Dataset: ' + label + '\n' + '\n'.join(
        k + ': ' + v for k, v in fields.items() if k and v)
