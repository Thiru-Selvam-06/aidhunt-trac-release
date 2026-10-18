"""Prepare a fresh clone for demo builds, without touching an existing Firebase configuration."""
import json,sys
from pathlib import Path
def prepare(root):
    template=json.loads((root/"app/src/deviceTest/google-services.json").read_text(encoding="utf-8-sig"))
    if template["project_info"]["project_id"]!="demo-aidhunt-v16": raise ValueError("Expected demo template")
    template["client"][0]["client_info"]["android_client_info"]["package_name"]="com.aistudio.aidhunttrac.cedlir"
    target=root/"app/google-services.json"
    if target.exists():
        current=json.loads(target.read_text(encoding="utf-8-sig"))
        if current!=template: raise ValueError("Existing Firebase config preserved. Use a fresh clone for demo setup.")
        return False
    target.write_text(json.dumps(template,indent=2)+"\n",encoding="utf-8");return True
if __name__=="__main__":
    try: print("Demo compile configuration created" if prepare(Path(__file__).resolve().parents[1]) else "Demo compile configuration already present")
    except (ValueError,KeyError) as e: sys.exit(str(e))
