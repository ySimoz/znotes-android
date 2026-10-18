#!/usr/bin/env python3
"""
Automated export of all production assets for zNote branding across:
- docs/branding/ (SVGs, PNG masters, lockups)
- app/src/main/res/ (mipmap densities: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- app/src/main/res/drawable/ic_launcher_monochrome.xml (themed Material You vector)
- fastlane/metadata/android/en-US/images/icon.png (store listing)
"""

import os
import subprocess

OUT_DIR = "docs/branding"
RES_DIR = "app/src/main/res"

FOREGROUND_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432
}

ICON_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

def run(cmd):
    print("Running:", cmd)
    subprocess.check_call(cmd, shell=True)

def main():
    os.makedirs(f"{OUT_DIR}/renders", exist_ok=True)
    
    # 1. Master PNGs in docs/branding/renders/
    run(f"rsvg-convert -w 512 -h 512 {OUT_DIR}/znote-symbol.svg -o {OUT_DIR}/renders/znote-symbol-512.png")
    run(f"rsvg-convert -w 1024 -h 1024 {OUT_DIR}/znote-symbol.svg -o {OUT_DIR}/renders/znote-symbol-1024.png")
    run(f"rsvg-convert -w 512 -h 512 {OUT_DIR}/znote-symbol-transparent.svg -o {OUT_DIR}/renders/znote-symbol-transparent-512.png")
    run(f"rsvg-convert -w 1536 -h 512 {OUT_DIR}/znote-lockup.svg -o {OUT_DIR}/renders/znote-lockup-1536.png")

    # 2. Fastlane store icon (512x512)
    run(f"rsvg-convert -w 512 -h 512 {OUT_DIR}/znote-symbol.svg -o fastlane/metadata/android/en-US/images/icon.png")

    # 3. Android adaptive icon foregrounds
    fg_svg = f"{OUT_DIR}/znote-adaptive-foreground.svg"
    for density, size in FOREGROUND_SIZES.items():
        dst = f"{RES_DIR}/mipmap-{density}/ic_launcher_foreground.png"
        run(f"rsvg-convert -w {size} -h {size} {fg_svg} -o {dst}")

    # 4. Android legacy icons (ic_launcher.png and circular ic_launcher_round.png)
    sym_svg = f"{OUT_DIR}/znote-symbol.svg"
    for density, size in ICON_SIZES.items():
        dst_square = f"{RES_DIR}/mipmap-{density}/ic_launcher.png"
        dst_round = f"{RES_DIR}/mipmap-{density}/ic_launcher_round.png"
        run(f"rsvg-convert -w {size} -h {size} {sym_svg} -o {dst_square}")
        # Create circular clip using convert
        mask_cmd = (
            f"convert {dst_square} \\( +clone -alpha extract "
            f"-draw 'fill black polygon 0,0 0,{size} {size},{size} {size},0 fill white circle {size/2},{size/2} {size/2},0' "
            f"\\) -alpha off -compose CopyOpacity -composite {dst_round}"
        )
        run(mask_cmd)

    # 5. Android Material You monochrome vector layer
    from build_production_kit import make_android_monochrome_xml
    with open(f"{RES_DIR}/drawable/ic_launcher_monochrome.xml", "w") as f:
        f.write(make_android_monochrome_xml())
        
    print("All production assets and Android app icons generated successfully!")

if __name__ == "__main__":
    main()
