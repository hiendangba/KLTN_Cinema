import os
from docx import Document


def main():
    path = os.environ["DOCX_PATH"]
    doc = Document(path)
    texts = [p.text.strip() for p in doc.paragraphs if p.text.strip()]
    print("TOTAL", len(texts))
    for i in range(490, min(520, len(texts))):
        print(f"{i}: {texts[i].encode('unicode_escape').decode('ascii')}")


if __name__ == "__main__":
    main()
