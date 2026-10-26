"""Render Google Play creatives from real screenshots of this build."""
from pathlib import Path
import argparse, json, hashlib
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[2]
FONT = ROOT / "android/app/src/main/res/font/inter.ttf"
SLIDES = [
 ("overview", ["Кредит. Вклад.", "Лизинг."], "Все расчёты — в одном приложении", "31-five-section-menu-orange", "amber"),
 ("loan", ["Платежи", "по месяцам"], "График кредита и подробный PDF-отчёт", "11-loan-schedule", "light"),
 ("leasing", ["Лизинг", "в цифрах"], "Аванс, выкуп, срок и стоимость", "33-leasing-direct", "amber"),
 ("deposit", ["Планируйте", "доход по вкладу"], "Капитализация и регулярные пополнения", "04-deposit-results", "light"),
 ("reverse", ["Узнайте ставку", "по платежам"], "Обратный расчёт условий лизинга", "40-leasing-reverse-result", "dark"),
 ("rate", ["Понятно,", "откуда ставка"], "Формулы и пояснения к расчётам", "41-leasing-rate-info", "light"),
 ("history", ["Сохраните", "важные расчёты"], "История кредитов, вкладов и лизинга", "36-history-leasing", "amber"),
 ("theme", ["Ваш цвет.", "Ваш DepoCred."], "Светлая и тёмная темы · 5 цветов", "05-info-dark", "dark")
]
SIZES = {"phoneScreenshots": (1080,1920), "sevenInchScreenshots": (1200,1920), "tenInchScreenshots": (1600,2560)}

def font(size, bold=False):
    f = ImageFont.truetype(str(FONT), int(size))
    try: f.set_variation_by_axes([(700 if bold else 450) if axis["name"] == b"Weight" else axis["default"] for axis in f.get_variation_axes()])
    except (OSError, ValueError): pass
    return f

def draw_text(draw, xy, value, size, color, bold=False, max_width=None):
    f = font(size, bold)
    while max_width and draw.textlength(value, font=f) > max_width:
        size -= 1; f = font(size, bold)
    draw.text(xy, value, fill=color, font=f, anchor="lt")

def render(source, kind, output):
    w,h = SIZES[kind]; scale=w/1080; margin=round(w*.07)
    dest=output/"ru-RU"/"images"/kind; dest.mkdir(parents=True,exist_ok=True)
    manifest=[]
    for i,(name,lines,subtitle,shot_name,style) in enumerate(SLIDES,1):
        shot_path=source/(shot_name+".png")
        with Image.open(shot_path) as original:
            original.load(); shot=original.convert("RGB")
        if kind!="phoneScreenshots" and shot.width/shot.height < .60:
            raise ValueError("Tablet images must come from a tablet capture, not a phone")
        bg={"amber":"#efab36","light":"#fbf6ed","dark":"#152632"}[style]
        ink="#ffffff" if style=="dark" else "#17252d"
        muted="#c1ccd6" if style=="dark" else "#45515a"
        canvas=Image.new("RGB",(w,h),bg); draw=ImageDraw.Draw(canvas)
        for r in (.45,.59,.73,.87):
            rad=int(w*r); cx=w//2; cy=int(h*.78)
            draw.ellipse((cx-rad,cy-rad,cx+rad,cy+rad),outline={"amber":"#f2bd60","light":"#efdfc5","dark":"#283c48"}[style],width=2)
        draw_text(draw,(margin,round(55*scale)),"DepoCred",44*scale,ink,True)
        for line_n,line in enumerate(lines):
            draw_text(draw,(margin,round((145+line_n*94)*scale)),line,88*scale,ink,True,w-2*margin)
        draw_text(draw,(margin,round(355*scale)),subtitle,31*scale,muted,max_width=w-2*margin)
        top=round(445*scale); bottom=h-round(95*scale)
        max_w=w-2*margin-24; max_h=bottom-top
        shot=ImageOps.contain(shot,(max_w,max_h),Image.Resampling.LANCZOS)
        x=(w-shot.width)//2; y=top
        border=max(5,round(8*scale)); radius=round(38*scale)
        draw.rounded_rectangle((x-border,y-border,x+shot.width+border,y+shot.height+border),radius=radius,fill="#253640")
        mask=Image.new("L",shot.size,0)
        ImageDraw.Draw(mask).rounded_rectangle((0,0,shot.width-1,shot.height-1),radius=max(4,radius-border),fill=255)
        canvas.paste(shot,(x,y),mask)
        draw_text(draw,(margin,h-round(48*scale)),"КРЕДИТ · ВКЛАД · ЛИЗИНГ",22*scale,ink,True)
        draw_text(draw,(w-margin-round(95*scale),h-round(48*scale)),f"{i:02d} / 08",22*scale,ink)
        target=dest/f"{i:02d}-{name}.jpg"
        canvas.save(target,quality=96,subsampling=0)
        with Image.open(target) as check:
            assert check.size==(w,h) and check.mode=="RGB"
        manifest.append({"image":str(target.relative_to(output)),"source":shot_name+".png","source_sha256":hashlib.sha256(shot_path.read_bytes()).hexdigest(),"dimensions":[w,h]})
    (output/f"manifest-{kind}.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    if kind=="phoneScreenshots":
        banner=Image.new("RGB",(1024,500),"#efab36"); d=ImageDraw.Draw(banner)
        draw_text(d,(60,55),"DepoCred",48,"#17252d",True)
        draw_text(d,(60,145),"Кредит. Вклад. Лизинг.",65,"#17252d",True,904)
        draw_text(d,(60,245),"Считайте условия. Сравнивайте варианты.",32,"#45515a",max_width=904)
        draw_text(d,(60,305),"Графики · История · PDF",32,"#45515a")
        draw_text(d,(60,420),"БЕЗ РЕГИСТРАЦИИ · РАСЧЁТЫ ОФЛАЙН",22,"#17252d",True)
        banner.save(dest.parent/"featureGraphic.png")
    print(f"Rendered {len(manifest)} {kind} creatives")

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--source",type=Path,required=True)
    parser.add_argument("--kind",choices=SIZES,required=True)
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args()
    render(args.source,args.kind,args.output)
