from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import re
import unicodedata


FIELDS = {"document_id", "title", "source", "license", "text"}
DOCUMENT_ID = re.compile(r"[a-z0-9][a-z0-9._-]{0,127}\Z")


class DocumentError(ValueError):
    pass


@dataclass(frozen=True)
class Document:
    document_id: str
    title: str
    source: str
    license: str
    text: str


def _text(value: object, field: str, line: int) -> str:
    if not isinstance(value, str):
        raise DocumentError(f"line {line}: {field} must be a string")
    normalized = unicodedata.normalize("NFKC", value).replace("\r\n", "\n").replace("\r", "\n").strip()
    if not normalized:
        raise DocumentError(f"line {line}: {field} must be non-blank")
    return normalized


def read_documents(path: Path) -> list[Document]:
    documents: list[Document] = []
    seen: set[str] = set()
    with path.open("r", encoding="utf-8", newline=None) as source:
        for line_number, line in enumerate(source, 1):
            if not line.strip():
                raise DocumentError(f"line {line_number}: blank lines are not allowed")
            try:
                def strict_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
                    value: dict[str, object] = {}
                    for key, item in pairs:
                        if key in value:
                            raise DocumentError(f"line {line_number}: duplicate JSON field: {key}")
                        value[key] = item
                    return value

                row = json.loads(line, object_pairs_hook=strict_object)
            except json.JSONDecodeError as error:
                raise DocumentError(f"line {line_number}: invalid JSON: {error.msg}") from error
            if not isinstance(row, dict):
                raise DocumentError(f"line {line_number}: row must be an object")
            if set(row) != FIELDS:
                missing = sorted(FIELDS - set(row))
                unknown = sorted(set(row) - FIELDS)
                raise DocumentError(
                    f"line {line_number}: fields mismatch; missing={missing}, unknown={unknown}"
                )
            document_id = _text(row["document_id"], "document_id", line_number)
            if not DOCUMENT_ID.fullmatch(document_id):
                raise DocumentError(f"line {line_number}: invalid document_id")
            if document_id in seen:
                raise DocumentError(f"line {line_number}: duplicate document_id: {document_id}")
            seen.add(document_id)
            documents.append(
                Document(
                    document_id=document_id,
                    title=_text(row["title"], "title", line_number),
                    source=_text(row["source"], "source", line_number),
                    license=_text(row["license"], "license", line_number),
                    text=_text(row["text"], "text", line_number),
                )
            )
    if not documents:
        raise DocumentError("input contains no documents")
    return sorted(documents, key=lambda document: document.document_id.encode("utf-8"))
