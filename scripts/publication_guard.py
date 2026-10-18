"""Check the Git index/current committed tree without printing sensitive values."""
import argparse,json,re,subprocess,sys,zipfile,io
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def issues(name,data):
    p=Path(name);out=[]
    if p.name in {".env","google-services.json"} and name!="app/src/deviceTest/google-services.json": out.append("local environment/configuration")
    if p.suffix.lower() in {".jks",".keystore",".p12",".pfx"} or "keystore.base64" in name: out.append("signing credential")
    if "node_modules" in p.parts or "__pycache__" in p.parts or name.startswith(("docs/test-artifacts/","migration-private/","qodex/")): out.append("private/generated evidence or unrelated checkout")
    if p.suffix==".docx":
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as z: data=b"\n".join(z.read(n) for n in z.namelist() if n.endswith(".xml"))
        except zipfile.BadZipFile: out.append("invalid document")
    text=data.decode("utf-8",errors="replace")
    patterns=[
      ("private-key material",r"-----BEGIN (?:RSA |EC )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{128,}"),
      ("Cloudflare token",r"\bcfat_[A-Za-z0-9_-]{30,}"),
      ("OAuth refresh token",r"\b1//[A-Za-z0-9_-]{40,}"),
      ("GitHub token",r"\b(?:ghp_|github_pat_)[A-Za-z0-9_]{30,}"),
      ("Firebase/Google ID token",r"\beyJ[A-Za-z0-9_-]{20,}\.eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{30,}"),
    ]
    for label,pattern in patterns:
        if re.search(pattern,text):out.append(label)
    return out
def inspect(root=ROOT):
    paths=subprocess.check_output(["git","ls-files","-z"],cwd=root).decode().split("\0")
    results=[]
    for name in filter(None,paths):
        # Index bytes, not worktree bytes: this is the exact proposed publication.
        data=subprocess.check_output(["git","show",":"+name],cwd=root)
        reasons=issues(name,data)
        if reasons:results.append({"path":name,"reasons":reasons})
    return results
def main():
    result=inspect();print(json.dumps({"findings":result,"valuesPrinted":False,
        "scope":"Git index; heuristic scanner, not a guarantee against every possible secret"},indent=2))
    return int(bool(result))
if __name__=="__main__":sys.exit(main())
