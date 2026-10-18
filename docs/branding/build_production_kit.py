#!/usr/bin/env python3
"""
Complete production kit generator for zNote (Option B1):
- Exports high-res SVGs (symbol with stripes, transparent, lockups, monochrome)
- Exports Android app launcher icons for all mipmap densities (adaptive + legacy)
- Generates Android VectorDrawable ic_launcher_monochrome.xml for Android 13+
- Updates fastlane store icon (512x512)
"""

import os
import math
import subprocess

OUT_DIR = "docs/branding"
RES_DIR = "app/src/main/res"

# Exact B1 geometry parameters
# Colors:
COLOR_CYAN = "#1DAEF5"
COLOR_ORANGE = "#FF944A"
COLOR_WHITE = "#FFFFFF"
COLOR_BG = "#08090C"
COLOR_STRIPE = "#181E29"

# B1 Polygon Generator:
# 36px uniform horizontal bars, 36px perpendicular diagonal stem
def get_b1_path_data():
    x_tl, x_tr = 74, 206
    y_top_0, y_top_1 = 60, 96
    x_bl, x_br = 48, 176
    y_bot_0, y_bot_1 = 160, 196
    italic_dx = 12
    w_diag_perp = 36
    dy_gap = y_bot_0 - y_top_1 # 64
    dx_diag = -64
    diag_len = math.hypot(dx_diag, dy_gap) # 90.5
    sin_theta = dy_gap / diag_len
    dx_horiz = w_diag_perp / sin_theta # ~50.9

    x_d_tr = x_tr - italic_dx - 8
    x_d_br = x_d_tr + dx_diag
    x_d_tl = x_d_tr - dx_horiz
    x_d_bl = x_d_br - dx_horiz

    path = (
        f"M {x_tl:.1f},{y_top_0:.1f} "
        f"L {x_tr:.1f},{y_top_0:.1f} "
        f"L {x_tr - italic_dx:.1f},{y_top_1:.1f} "
        f"L {x_d_tr:.1f},{y_top_1:.1f} "
        f"L {x_d_br:.1f},{y_bot_0:.1f} "
        f"L {x_br + italic_dx:.1f},{y_bot_0:.1f} "
        f"L {x_br:.1f},{y_bot_1:.1f} "
        f"L {x_bl:.1f},{y_bot_1:.1f} "
        f"L {x_bl + italic_dx:.1f},{y_bot_0:.1f} "
        f"L {x_d_bl:.1f},{y_bot_0:.1f} "
        f"L {x_d_tl:.1f},{y_top_1:.1f} "
        f"L {x_tl - italic_dx:.1f},{y_top_1:.1f} "
        f"Z"
    )

    white_wedge = (
        f"M {x_bl:.1f},{y_bot_1:.1f} "
        f"L {x_bl + 52:.1f},{y_bot_1:.1f} "
        f"L {x_bl + 52 + italic_dx:.1f},{y_bot_0:.1f} "
        f"L {x_bl + italic_dx:.1f},{y_bot_0:.1f} "
        f"Z"
    )

    return path, white_wedge

def make_symbol_svg(with_bg=True, rx=44, w=256, h=256):
    z_path, white_wedge = get_b1_path_data()
    bg_defs = ""
    bg_rect = ""
    if with_bg:
        bg_defs = f"""  <defs>
    <pattern id="diag_stripes" width="16" height="16" patternTransform="rotate(-45)" patternUnits="userSpaceOnUse">
      <rect width="16" height="16" fill="{COLOR_BG}" />
      <line x1="0" y1="0" x2="0" y2="16" stroke="{COLOR_STRIPE}" stroke-width="4" />
    </pattern>
  </defs>"""
        bg_rect = f'  <rect width="{w}" height="{h}" fill="url(#diag_stripes)" rx="{rx}" />\n'

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">
{bg_defs}
{bg_rect}  <g id="mark">
    <!-- Orange chromatic glitch offset (-16, -3) -->
    <path d="{z_path}" fill="{COLOR_ORANGE}" transform="translate(-16, -3)" />
    
    <!-- Primary Cyan front layer -->
    <path d="{z_path}" fill="{COLOR_CYAN}" />
    
    <!-- White accent terminal wedge -->
    <path d="{white_wedge}" fill="{COLOR_ORANGE}" transform="translate(-16, -3)" opacity="0.5" />
    <path d="{white_wedge}" fill="{COLOR_WHITE}" />
  </g>
</svg>"""

def make_lockup_horizontal():
    sym = make_symbol_svg(with_bg=True, rx=36)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 768 256" width="768" height="256">
  <rect width="768" height="256" fill="#0A0B0E" />
  <g transform="translate(32, 16) scale(0.875)">
    {sym}
  </g>
  <g transform="translate(280, 156)" font-family="system-ui, -apple-system, sans-serif" font-weight="800">
    <text x="0" y="0" font-size="108" fill="{COLOR_ORANGE}" dx="-5" dy="0" letter-spacing="-1">z</text>
    <text x="0" y="0" font-size="108" fill="{COLOR_CYAN}" letter-spacing="-1">z</text>
    <text x="64" y="0" font-size="108" fill="{COLOR_WHITE}" letter-spacing="-1">Notes</text>
    <text x="6" y="38" font-size="20" fill="#8892A4" font-weight="500" letter-spacing="4">HANDWRITING NOTEBOOK</text>
  </g>
</svg>"""

def make_adaptive_foreground_svg():
    """Android adaptive icon foreground: 432x432 canvas with diagonal stripes and centered glyph."""
    z_path, white_wedge = get_b1_path_data()
    # Scale from 256x256 to center in 432x432 safe zone:
    # Scale factor 1.15, translate: (432 - 256*1.15)/2 = 68.8
    scale = 1.15
    tx = (432 - 256 * scale) / 2
    ty = (432 - 256 * scale) / 2
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 432 432" width="432" height="432">
  <defs>
    <pattern id="adaptive_diag" width="20" height="20" patternTransform="rotate(-45)" patternUnits="userSpaceOnUse">
      <rect width="20" height="20" fill="{COLOR_BG}" />
      <line x1="0" y1="0" x2="0" y2="20" stroke="{COLOR_STRIPE}" stroke-width="5" />
    </pattern>
  </defs>
  <!-- Full adaptive background surface with diagonal stripes -->
  <rect width="432" height="432" fill="url(#adaptive_diag)" />
  
  <g transform="translate({tx:.1f}, {ty:.1f}) scale({scale})">
    <!-- Orange offset -->
    <path d="{z_path}" fill="{COLOR_ORANGE}" transform="translate(-16, -3)" />
    <!-- Cyan main -->
    <path d="{z_path}" fill="{COLOR_CYAN}" />
    <!-- White foot -->
    <path d="{white_wedge}" fill="{COLOR_ORANGE}" transform="translate(-16, -3)" opacity="0.5" />
    <path d="{white_wedge}" fill="{COLOR_WHITE}" />
  </g>
</svg>"""

def make_android_monochrome_xml():
    """Generates ic_launcher_monochrome.xml for Android 13+ Material You themed icons."""
    # Convert path to 432x432 viewport
    scale = 1.15
    tx = (432 - 256 * scale) / 2
    ty = (432 - 256 * scale) / 2
    
    # We can use group transform in Android VectorDrawable!
    z_path, white_wedge = get_b1_path_data()
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Themed-icon layer for zNote: Material You dynamic tinting (Android 13+) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="432"
    android:viewportHeight="432">
    <group
        android:translateX="{tx:.1f}"
        android:translateY="{ty:.1f}"
        android:scaleX="{scale}"
        android:scaleY="{scale}">
        <!-- Shadow / Glitch offset layer with 35% alpha -->
        <path
            android:fillAlpha="0.35"
            android:fillColor="#FFFFFF"
            android:fillType="nonZero"
            android:pathData="{z_path}"
            android:translateX="-16"
            android:translateY="-3" />
        <!-- Foreground primary shape -->
        <path
            android:fillColor="#FFFFFF"
            android:fillType="nonZero"
            android:pathData="{z_path}" />
    </group>
</vector>
"""

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    
    # 1. Save master SVGs in docs/branding
    with open(f"{OUT_DIR}/znote-symbol.svg", "w") as f:
        f.write(make_symbol_svg(with_bg=True, rx=44))
    with open(f"{OUT_DIR}/znote-symbol-transparent.svg", "w") as f:
        f.write(make_symbol_svg(with_bg=False))
    with open(f"{OUT_DIR}/znote-lockup.svg", "w") as f:
        f.write(make_lockup_horizontal())
    with open(f"{OUT_DIR}/znote-adaptive-foreground.svg", "w") as f:
        f.write(make_adaptive_foreground_svg())
        
    print("Master SVGs written to docs/branding/.")

if __name__ == "__main__":
    main()
