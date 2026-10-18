#!/usr/bin/env python3
"""
Generate complete zNote logo concepts (symbols + lockups) and render
the presentation concept sheet according to the logo-design skill guidelines.
"""

import os
import math
import subprocess

OUT_DIR = "docs/branding"
SKILL_DIR = "/home/simo/.gemini/config/plugins/logo-design/skills/logo-design"

def get_diag_pattern(pat_id, angle=45, stripe_w=4, gap=12, stroke_color="#1A202C", bg_color="#090B0E"):
    step = stripe_w + gap
    return f"""  <defs>
    <pattern id="{pat_id}" width="{step}" height="{step}" patternTransform="rotate({angle})" patternUnits="userSpaceOnUse">
      <rect width="{step}" height="{step}" fill="{bg_color}" />
      <line x1="0" y1="0" x2="0" y2="{step}" stroke="{stroke_color}" stroke-width="{stripe_w}" />
    </pattern>
  </defs>"""

# -------------------------------------------------------------
# Concept A: Cyber Glitch Z (Heritage Redesign)
# -------------------------------------------------------------
def make_concept_a_symbol():
    pat = get_diag_pattern("pat_a", angle=45, stripe_w=3.5, gap=11, stroke_color="#1B222E", bg_color="#090A0D")
    
    # Path of the muscular geometric Z
    # Uniform 42px stroke throughout
    z_path = (
        "M 54,58 "
        "L 204,58 "
        "L 204,94 "
        "L 194,100 "
        "L 148,100 "
        "L 100,156 "
        "L 194,156 "
        "L 204,166 "
        "L 196,198 "
        "L 52,198 "
        "L 52,162 "
        "L 62,156 "
        "L 108,156 "
        "L 156,100 "
        "L 54,100 "
        "Z"
    )
    
    # Bold white terminal foot
    white_foot = (
        "M 52,198 "
        "L 104,198 "
        "L 122,156 "
        "L 62,156 "
        "L 52,162 "
        "Z"
    )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
{pat}
  <rect width="256" height="256" fill="url(#pat_a)" rx="44" />
  <g id="mark">
    <!-- Orange Chromatic Glitch Offset -->
    <path d="{z_path}" fill="#FF944A" transform="translate(-16, 0)" />
    
    <!-- Primary Cyan Front Layer -->
    <path d="{z_path}" fill="#1DAEF5" />
    
    <!-- White Accent Foot -->
    <path d="{white_foot}" fill="#FF944A" transform="translate(-16, 0)" opacity="0.6" />
    <path d="{white_foot}" fill="#FFFFFF" />
  </g>
</svg>"""

# -------------------------------------------------------------
# Concept B: Dynamic Italic Z (Chisel Nib / Velocity)
# -------------------------------------------------------------
def make_concept_b_symbol():
    pat = get_diag_pattern("pat_b", angle=-45, stripe_w=4, gap=12, stroke_color="#181E29", bg_color="#08090C")
    
    z_italic = (
        "M 74,58 "
        "L 208,58 "
        "L 194,100 "
        "L 146,100 "
        "L 96,154 "
        "L 186,154 "
        "L 174,198 "
        "L 46,198 "
        "L 60,154 "
        "L 110,154 "
        "L 160,100 "
        "L 88,100 "
        "Z"
    )
    
    white_wedge = (
        "M 46,198 "
        "L 98,198 "
        "L 116,154 "
        "L 60,154 "
        "Z"
    )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
{pat}
  <rect width="256" height="256" fill="url(#pat_b)" rx="44" />
  <g id="mark">
    <!-- Orange Offset (-18, -4) -->
    <path d="{z_italic}" fill="#FF944A" transform="translate(-18, -4)" />
    
    <!-- Cyan Layer -->
    <path d="{z_italic}" fill="#1DAEF5" />
    
    <!-- White Terminal -->
    <path d="{white_wedge}" fill="#FF944A" transform="translate(-18, -4)" opacity="0.5" />
    <path d="{white_wedge}" fill="#FFFFFF" />
  </g>
</svg>"""

# -------------------------------------------------------------
# Concept C: CRT Fracture Z (Glitch Loader Splicing)
# -------------------------------------------------------------
def make_concept_c_symbol():
    pat = get_diag_pattern("pat_c", angle=45, stripe_w=3.5, gap=10.5, stroke_color="#1B222E", bg_color="#090B0E")
    
    z_base = (
        "M 54,58 "
        "L 204,58 "
        "L 204,96 "
        "L 148,96 "
        "L 100,154 "
        "L 198,154 "
        "L 198,198 "
        "L 52,198 "
        "L 52,160 "
        "L 106,160 "
        "L 154,96 "
        "L 54,96 "
        "Z"
    )
    
    white_foot = (
        "M 52,198 "
        "L 102,198 "
        "L 120,160 "
        "L 66,160 "
        "L 52,168 "
        "Z"
    )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
{pat}
  <rect width="256" height="256" fill="url(#pat_c)" rx="44" />
  <g id="mark">
    <!-- Orange Layer -->
    <path d="{z_base}" fill="#FF944A" transform="translate(-16, 0)" />
    <rect x="44" y="120" width="166" height="16" fill="#FF944A" transform="translate(-12, 0)" opacity="0.9" />
    
    <!-- Cyan Layer -->
    <path d="{z_base}" fill="#1DAEF5" />
    <rect x="56" y="120" width="166" height="16" fill="#1DAEF5" />
    
    <!-- White Foot -->
    <path d="{white_foot}" fill="#FF944A" transform="translate(-16, 0)" opacity="0.6" />
    <path d="{white_foot}" fill="#FFFFFF" />
  </g>
</svg>"""

# -------------------------------------------------------------
# Lockups: 768x256 Canvas with "zNote" Wordmark
# -------------------------------------------------------------
def make_lockup(symbol_svg_inner, name_suffix=""):
    """Creates a horizontal lockup with the icon and stylized zNote typography."""
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 768 256" width="768" height="256">
  <rect width="768" height="256" fill="#0A0B0E" />
  <!-- Embedded Symbol -->
  <g transform="translate(32, 16) scale(0.875)">
    {symbol_svg_inner}
  </g>
  <!-- Wordmark 'zNote' -->
  <g transform="translate(280, 160)" font-family="system-ui, -apple-system, sans-serif" font-weight="800">
    <!-- 'z' in Cyan with Orange Glitch Shadow -->
    <text x="0" y="0" font-size="108" fill="#FF944A" dx="-5" dy="0" letter-spacing="-1">z</text>
    <text x="0" y="0" font-size="108" fill="#1DAEF5" letter-spacing="-1">z</text>
    
    <!-- 'Note' in crisp pure white -->
    <text x="64" y="0" font-size="108" fill="#FFFFFF" letter-spacing="-1">Note</text>
    
    <!-- Subtitle -->
    <text x="6" y="38" font-size="20" fill="#8892A4" font-weight="500" letter-spacing="4">HANDWRITING NOTEBOOK</text>
  </g>
</svg>"""

def main():
    os.makedirs(f"{OUT_DIR}/renders", exist_ok=True)
    
    # 1. Symbols
    sym_a = make_concept_a_symbol()
    sym_b = make_concept_b_symbol()
    sym_c = make_concept_c_symbol()
    
    with open(f"{OUT_DIR}/concept-a-symbol.svg", "w") as f: f.write(sym_a)
    with open(f"{OUT_DIR}/concept-b-symbol.svg", "w") as f: f.write(sym_b)
    with open(f"{OUT_DIR}/concept-c-symbol.svg", "w") as f: f.write(sym_c)
    
    # 2. Lockups
    with open(f"{OUT_DIR}/concept-a-lockup.svg", "w") as f: f.write(make_lockup(sym_a))
    with open(f"{OUT_DIR}/concept-b-lockup.svg", "w") as f: f.write(make_lockup(sym_b))
    with open(f"{OUT_DIR}/concept-c-lockup.svg", "w") as f: f.write(make_lockup(sym_c))
    
    print("Exported SVGs for symbols and lockups.")

if __name__ == "__main__":
    main()
