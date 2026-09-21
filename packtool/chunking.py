from __future__ import annotations

from dataclasses import dataclass

from .schema import Document


@dataclass(frozen=True)
class Chunk:
    chunk_id: str
    document_id: str
    title: str
    source: str
    text: str


def _boundary(text: str, start: int, target: int, overlap: int) -> int:
    minimum = start + overlap + 1
    paragraph = text.rfind("\n\n", minimum, target + 1)
    if paragraph >= minimum:
        return paragraph
    line = text.rfind("\n", minimum, target + 1)
    if line >= minimum:
        return line
    word = text.rfind(" ", max(minimum, start + (target - start) // 2), target + 1)
    return word if word >= minimum else target


def chunk_document(document: Document, max_chars: int = 1200, overlap_chars: int = 150) -> list[Chunk]:
    if max_chars <= 0 or overlap_chars < 0 or overlap_chars >= max_chars:
        raise ValueError("chunk sizes must satisfy 0 <= overlap_chars < max_chars")
    chunks: list[Chunk] = []
    start = 0
    while start < len(document.text):
        target = min(start + max_chars, len(document.text))
        end = target if target == len(document.text) else _boundary(document.text, start, target, overlap_chars)
        chunks.append(
            Chunk(
                chunk_id=f"{document.document_id}:{len(chunks):04d}",
                document_id=document.document_id,
                title=document.title,
                source=document.source,
                text=document.text[start:end],
            )
        )
        if end == len(document.text):
            break
        start = end - overlap_chars
    return chunks


def chunk_documents(documents: list[Document], max_chars: int = 1200, overlap_chars: int = 150) -> list[Chunk]:
    return [
        chunk
        for document in documents
        for chunk in chunk_document(document, max_chars=max_chars, overlap_chars=overlap_chars)
    ]
