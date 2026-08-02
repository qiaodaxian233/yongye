#!/usr/bin/env python3
"""只读分析 Minecraft Java / DragonCore 模型目录或 ZIP。

不会执行包内脚本。可检查模型、状态 JSON、纹理、动画元数据、
ItemModel.yml 映射、重复文件与路径风险，并输出 JSON/Markdown。

可选依赖：Pillow、PyYAML。
"""
from __future__ import annotations
import argparse, collections, hashlib, io, json, os, tempfile, zipfile
from pathlib import Path, PurePosixPath
from typing import Any

try:
    from PIL import Image
except Exception:
    Image = None
try:
    import yaml
except Exception:
    yaml = None

MODEL_DISPLAY_KEYS=("thirdperson_righthand","thirdperson_lefthand","firstperson_righthand","firstperson_lefthand","ground","gui","fixed","head")


def safe_extract(zf: zipfile.ZipFile, target: Path) -> None:
    root=target.resolve()
    for member in zf.infolist():
        out=(target/member.filename).resolve()
        if root not in out.parents and out != root:
            raise ValueError(f"ZIP 路径穿越: {member.filename}")
    zf.extractall(target)


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""):
            h.update(chunk)
    return h.hexdigest()


def rel(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def is_model_json(data: Any) -> bool:
    return isinstance(data,dict) and isinstance(data.get("elements"),list)


def model_report(path: Path, root: Path) -> dict[str,Any] | None:
    try:data=read_json(path)
    except Exception as e:return {"path":rel(path,root),"parse_error":str(e),"kind":"invalid_json"}
    if not is_model_json(data):return None
    elements=data.get("elements",[]); mins=[None]*3; maxs=[None]*3
    rotations=collections.Counter(); axes=collections.Counter(); refs=collections.Counter(); face_count=0; zero=0
    for e in elements:
        if not isinstance(e,dict):continue
        frm,to=e.get("from"),e.get("to")
        if isinstance(frm,list) and isinstance(to,list) and len(frm)==len(to)==3:
            for i in range(3):
                lo=min(frm[i],to[i]); hi=max(frm[i],to[i])
                mins[i]=lo if mins[i] is None else min(mins[i],lo)
                maxs[i]=hi if maxs[i] is None else max(maxs[i],hi)
            if any(abs(to[i]-frm[i])<1e-9 for i in range(3)):zero+=1
        rot=e.get("rotation")
        if isinstance(rot,dict):
            axes[str(rot.get("axis"))]+=1; rotations[str(rot.get("angle"))]+=1
        faces=e.get("faces",{})
        if isinstance(faces,dict):
            for face in faces.values():
                face_count+=1
                if isinstance(face,dict):refs[str(face.get("texture"))]+=1
    display=data.get("display",{}) if isinstance(data.get("display"),dict) else {}
    name=path.name
    return {
      "path":rel(path,root),"kind":"primary_model" if name=="model.json" else "state_model",
      "file_bytes":path.stat().st_size,"credit":data.get("credit"),"parent":data.get("parent"),
      "texture_size":data.get("texture_size"),"texture_slots":data.get("textures",{}),
      "element_count":len(elements),"rotated_element_count":sum(axes.values()),"zero_thickness_elements":zero,
      "rotation_axes":dict(axes),"rotation_angles":dict(rotations),"bounds":{"min":mins,"max":maxs},
      "extent":[maxs[i]-mins[i] if mins[i] is not None else None for i in range(3)],
      "face_count":face_count,"texture_reference_counts":dict(refs),
      "display_contexts":sorted(display.keys()),"missing_display_contexts":[k for k in MODEL_DISPLAY_KEYS if k not in display],
      "group_count":len(data.get("groups",[])) if isinstance(data.get("groups"),list) else 0,
    }


def png_report(path: Path, root: Path) -> dict[str,Any]:
    result={"path":rel(path,root),"file_bytes":path.stat().st_size,"sha256":sha256(path)}
    if Image is None:
        result["note"]="未安装 Pillow"; return result
    try:
        with Image.open(path) as im:
            result.update({"width":im.width,"height":im.height,"mode":im.mode,"rgba_bytes_estimate":im.width*im.height*4})
            if im.width and im.height%im.width==0:result["default_vertical_frame_count"]=im.height//im.width
            if "A" in im.getbands():
                hist=im.getchannel("A").histogram(); result["alpha_coverage_percent"]=round(sum(hist[1:])/(im.width*im.height)*100,2)
    except Exception as e:result["image_error"]=str(e)
    return result


def load_yaml(path: Path) -> tuple[Any,str|None]:
    text=path.read_text(encoding="utf-8-sig",errors="replace")
    if yaml is None:return None,"未安装 PyYAML"
    try:return yaml.safe_load(text),None
    except Exception as e:return None,str(e)


def resolve_texture(model_path:Path,value:str,root:Path,all_png:list[Path]) -> dict[str,Any]:
    if ":" in value:
        return {"value":value,"status":"namespaced_not_resolved"}
    candidate=model_path.parent/(value+".png")
    if candidate.exists():return {"value":value,"status":"local","path":rel(candidate,root)}
    base=PurePosixPath(value).name+".png"
    same_package=[]
    top=rel(model_path,root).split('/')[0]
    for p in all_png:
        rp=rel(p,root)
        if p.name==base and rp.split('/')[0]==top:same_package.append(rp)
    return {"value":value,"status":"missing_local","candidate_paths":same_package[:20]}


def itemmodel_entries(path:Path,root:Path,model_paths:set[str]) -> dict[str,Any]:
    data,error=load_yaml(path); out={"path":rel(path,root),"entries":[],"parse_error":error}
    if error or not isinstance(data,dict):return out
    seen_match=collections.Counter(); seen_path=collections.Counter()
    for key,val in data.items():
        if not isinstance(val,dict) or "path" not in val:continue
        match=val.get("match"); mapped=str(val.get("path","")); seen_match[str(match)]+=1; seen_path[mapped]+=1
        suffix="/models/items/"+mapped.strip('/')+"/model.json"
        direct=[m for m in model_paths if m.endswith(suffix)]
        if not direct:
            suffix2="/"+mapped.strip('/')+"/model.json"; direct=[m for m in model_paths if m.endswith(suffix2)]
        out["entries"].append({"key":str(key),"match":match,"path":mapped,"resolved_models":direct})
    out["duplicate_matches"]=[k for k,v in seen_match.items() if k not in ("None","") and v>1]
    out["duplicate_paths"]=[k for k,v in seen_path.items() if k and v>1]
    out["missing_path_entries"]=[e for e in out["entries"] if not e["resolved_models"]]
    return out


def analyze(root:Path) -> dict[str,Any]:
    files=[p for p in root.rglob('*') if p.is_file()]
    ext=collections.Counter(p.suffix.lower() for p in files)
    json_paths=[p for p in files if p.suffix.lower()=='.json' and not p.name.endswith('.mcmeta')]
    png_paths=[p for p in files if p.suffix.lower()=='.png']
    meta_paths=[p for p in files if p.suffix.lower()=='.mcmeta']
    yml_paths=[p for p in files if p.suffix.lower() in {'.yml','.yaml'}]
    models=[]; other_json=[]
    for p in json_paths:
        r=model_report(p,root)
        if r is None:other_json.append(rel(p,root))
        else:models.append(r)
    model_paths={m['path'] for m in models if m.get('kind') in {'primary_model','state_model'}}
    textures=[png_report(p,root) for p in png_paths]
    # resolve texture slots
    missing=[]; namespaced=[]
    by_model={m['path']:m for m in models if 'texture_slots' in m}
    for m in models:
        if 'texture_slots' not in m:continue
        mp=root/m['path']; resolutions=[]
        slots=m.get('texture_slots') if isinstance(m.get('texture_slots'),dict) else {}
        for k,v in slots.items():
            if k=='particle' or not isinstance(v,str):continue
            rr=resolve_texture(mp,v,root,png_paths); rr['slot']=k; resolutions.append(rr)
            if rr['status']=='missing_local':missing.append({'model':m['path'],**rr})
            elif rr['status']=='namespaced_not_resolved':namespaced.append({'model':m['path'],**rr})
        m['texture_resolution']=resolutions
    # animation pairs
    png_set={rel(p,root):p for p in png_paths}; animation=[]; orphan=[]; bad=[]
    for meta in meta_paths:
        mr=rel(meta,root); expected=mr[:-7] # remove .mcmeta
        pair=png_set.get(expected)
        entry={'metadata':mr,'texture':expected if pair else None}
        try:d=read_json(meta); a=d.get('animation',{}) if isinstance(d,dict) else {}
        except Exception as e:bad.append({'metadata':mr,'error':str(e)}); continue
        entry['frametime']=a.get('frametime',1); entry['interpolate']=a.get('interpolate',False)
        if not pair:
            orphan.append(entry); animation.append(entry); continue
        if Image is not None:
            try:
                with Image.open(pair) as im:
                    fw=a.get('width',im.width); fh=a.get('height',fw)
                    count=(im.width//fw)*(im.height//fh) if fw and fh and im.width%fw==0 and im.height%fh==0 else None
                    entry.update({'image_size':[im.width,im.height],'frame_size':[fw,fh],'frame_count':count,'rgba_bytes_estimate':im.width*im.height*4})
                    frames=a.get('frames')
                    if isinstance(frames,list) and count is not None:
                        idx=[x.get('index') if isinstance(x,dict) else x for x in frames]
                        invalid=[x for x in idx if isinstance(x,int) and not 0<=x<count]
                        if invalid:bad.append({'metadata':mr,'invalid_frame_indices':invalid,'frame_count':count})
            except Exception as e:bad.append({'metadata':mr,'image_error':str(e)})
        if isinstance(entry['frametime'],(int,float)) and entry['frametime']<=0:bad.append({'metadata':mr,'invalid_frametime':entry['frametime']})
        animation.append(entry)
    paired_textures={a['texture'] for a in animation if a.get('texture')}
    vertical_without_meta=[]
    for t in textures:
        if t.get('default_vertical_frame_count',1)>1 and t['path'] not in paired_textures:vertical_without_meta.append(t['path'])
    # YAML
    itemmodels=[itemmodel_entries(p,root,model_paths) for p in yml_paths if p.name.lower()=='itemmodel.yml']
    # duplicates
    groups=collections.defaultdict(list)
    for p in files:
        try:groups[sha256(p)].append(rel(p,root))
        except Exception:pass
    dup=[v for v in groups.values() if len(v)>1]
    total_rgba=sum(t.get('rgba_bytes_estimate',0) for t in textures)
    unique_png={t['sha256']:t for t in textures if 'sha256' in t}
    unique_rgba=sum(t.get('rgba_bytes_estimate',0) for t in unique_png.values())
    # package summary
    packages=collections.defaultdict(collections.Counter)
    for p in files:
        rp=rel(p,root); top=rp.split('/')[0]; packages[top]['files']+=1; packages[top]['bytes']+=p.stat().st_size
        packages[top][p.suffix.lower()]+=1
    # paths
    lower=collections.defaultdict(list)
    for p in files:lower[rel(p,root).casefold()].append(rel(p,root))
    report={
      'root':str(root),'file_count':len(files),'extension_counts':dict(ext),
      'package_count':len(packages),'packages':[{'name':k,**dict(v)} for k,v in sorted(packages.items(),key=lambda kv:kv[1]['files'],reverse=True)],
      'json_count':len(json_paths),'primary_model_count':sum(m.get('kind')=='primary_model' for m in models),
      'state_model_count':sum(m.get('kind')=='state_model' for m in models),'other_json':other_json,
      'models':models,'texture_count':len(textures),'textures':textures,
      'missing_local_texture_reference_count':len(missing),'missing_local_texture_references':missing,
      'namespaced_texture_reference_count':len(namespaced),'namespaced_texture_references':namespaced,
      'animation_metadata_count':len(meta_paths),'animations':animation,'orphan_metadata':orphan,'bad_animation_metadata':bad,
      'vertical_textures_without_metadata':vertical_without_meta,
      'itemmodel_configs':itemmodels,
      'duplicate_group_count':len(dup),'duplicate_file_count':sum(len(x) for x in dup),
      'largest_duplicate_groups':sorted(dup,key=len,reverse=True)[:50],
      'rgba_bytes_estimate_total':total_rgba,'rgba_bytes_estimate_unique_by_png_hash':unique_rgba,
      'path_risks':{
        'non_ascii_file_count':sum(any(ord(c)>127 for c in rel(p,root)) for p in files),
        'space_file_count':sum(' ' in rel(p,root) for p in files),
        'parenthesis_file_count':sum(('(' in rel(p,root) or ')' in rel(p,root)) for p in files),
        'desktop_ini':[rel(p,root) for p in files if p.name.lower()=='desktop.ini'],
        'case_insensitive_collisions':[v for v in lower.values() if len(v)>1],
      },
      'optional_dependencies':{'Pillow':Image is not None,'PyYAML':yaml is not None}
    }
    return report


def mib(n:int|float)->float:return round(n/1024/1024,2)

def markdown(report:dict[str,Any])->str:
    models=[m for m in report['models'] if 'element_count' in m]
    max_model=max(models,key=lambda m:m['element_count']) if models else None
    missing_maps=sum(len(x.get('missing_path_entries',[])) for x in report['itemmodel_configs'])
    lines=['# 模型包静态分析报告','',f"- 文件：{report['file_count']}",f"- 顶层包：{report['package_count']}",f"- 主模型：{report['primary_model_count']}",f"- 状态模型：{report['state_model_count']}",f"- PNG：{report['texture_count']}",f"- 动画元数据：{report['animation_metadata_count']}",f"- ItemModel.yml：{len(report['itemmodel_configs'])}",'']
    lines+=['## 主要风险','',f"- 本地贴图引用未解析：{report['missing_local_texture_reference_count']}",f"- YAML 映射未解析：{missing_maps}",f"- 孤立动画元数据：{len(report['orphan_metadata'])}",f"- 动画元数据异常：{len(report['bad_animation_metadata'])}",f"- 竖向贴图但无元数据：{len(report['vertical_textures_without_metadata'])}",f"- 重复文件组：{report['duplicate_group_count']}（涉及 {report['duplicate_file_count']} 个文件）",f"- PNG 粗略 RGBA：{mib(report['rgba_bytes_estimate_total'])} MiB；按内容去重后 {mib(report['rgba_bytes_estimate_unique_by_png_hash'])} MiB",'']
    if max_model:lines+=['## 最高几何复杂度','',f"- `{max_model['path']}`：{max_model['element_count']} elements，{max_model['file_bytes']} bytes",'']
    lines+=['## 路径风险','',f"- 非 ASCII 文件：{report['path_risks']['non_ascii_file_count']}",f"- 含空格文件：{report['path_risks']['space_file_count']}",f"- 含括号文件：{report['path_risks']['parenthesis_file_count']}",f"- desktop.ini：{len(report['path_risks']['desktop_ini'])}",'', '> 这是静态报告。命名空间贴图、插件状态触发和运行时性能仍需在实际 DragonCore 版本中验证。','']
    return '\n'.join(lines)


def main()->None:
    ap=argparse.ArgumentParser(); ap.add_argument('input',type=Path); ap.add_argument('--output',type=Path); ap.add_argument('--markdown-output',type=Path); args=ap.parse_args()
    if not args.input.exists():raise SystemExit(f"不存在: {args.input}")
    if args.input.is_file() and zipfile.is_zipfile(args.input):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            with zipfile.ZipFile(args.input) as zf:safe_extract(zf,root)
            report=analyze(root); report['source_zip']=str(args.input)
    elif args.input.is_dir():report=analyze(args.input)
    else:raise SystemExit('输入必须是目录或 ZIP')
    text=json.dumps(report,ensure_ascii=False,indent=2)
    if args.output:args.output.write_text(text+'\n',encoding='utf-8'); print(args.output)
    else:print(text)
    if args.markdown_output:args.markdown_output.write_text(markdown(report),encoding='utf-8'); print(args.markdown_output)

if __name__=='__main__':main()
