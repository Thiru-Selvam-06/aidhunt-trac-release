"""Prepare synthetic Firebase config on disposable CI runners only; never run on a user's phone."""
import json,os
from pathlib import Path
if os.environ.get("CI")!="true": raise SystemExit("REFUSED: only disposable CI runners")
root=Path(__file__).resolve().parents[1]
template=json.loads((root/"app/src/deviceTest/google-services.json").read_text(encoding="utf-8-sig"))
template["client"][0]["client_info"]["android_client_info"]["package_name"]="com.aistudio.aidhunttrac.cedlir"
(root/"app/google-services.json").write_text(json.dumps(template,indent=2)+"\n",encoding="utf-8")
print("CI debug Firebase project: demo-aidhunt-v16; no live project credentials")
