#!/usr/bin/env python3
"""
Refine the 3 zNote logo concepts with precise optical weight,
chunky stroke thickness matching the original icon, and clean SVG paths.
"""

import os
import subprocess

OUT_DIR = "docs/branding"

def make_bg(angle=45, stripe_w=4, gap=12, stroke_color="#1A202A", bg_color="#090B0E"):
    pattern_id = f"bg_pat_{angle}_{stripe_w}_{gap}".replace("-", "m")
    step = stripe_w + gap
    return f"""  <defs>
    <pattern id="{pattern_id}" width="{step}" height="{step}" patternTransform="rotate({angle})" patternUnits="userSpaceOnUse">
      <rect width="{step}" height="{step}" fill="{bg_color}" />
      <line x1="0" y1="0" x2="0" y2="{step}" stroke="{stroke_color}" stroke-width="{stripe_w}" />
    </pattern>
  </defs>
  <rect width="256" height="256" fill="url(#{pattern_id})" rx="40" />"""

def make_concept_a():
    """
    Concept A: 'Cyber Glitch Z'
    Chunky, powerful glyph directly adopting the X-notes cyber-serif and bevel language.
    """
    bg = make_bg(angle=45, stripe_w=3.5, gap=11.5, stroke_color="#1B212C", bg_color="#090A0D")
    
    # Precise, heavy Z path matching the chunky weight of the original X
    # Top bar: y=60 to 102 (height 42)
    # Bottom bar: y=154 to 196 (height 42)
    # Diagonal stem thickness ~44px
    z_main = (
        "M 56,60 "
        "L 204,60 "
        "L 204,96 "
        "L 194,102 "
        "L 152,102 "
        "L 98,154 "
        "L 196,154 "
        "L 206,164 "
        "L 198,196 "
        "L 52,196 "
        "L 52,160 "
        "L 62,154 "
        "L 104,154 "
        "L 158,102 "
        "L 56,102 "
        "Z"
    )
    
    # White accent foot (at bottom left terminal)
    foot_white = (
        "M 52,196 "
        "L 100,196 "
        "L 118,154 "
        "L 62,154 "
        "L 52,160 "
        "Z"
    )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
{bg}
  <g id="mark">
    <!-- Orange Chromatic Glitch Offset -->
    <path d="{z_main}" fill="#FF944A" transform="translate(-16, 0)" />
    
    <!-- Primary Cyan Front Layer -->
    <path d="{z_main}" fill="#1DAEF5" />
    
    <!-- Crisp White Terminal Foot -->
    <path d="{foot_white}" fill="#FF944A" transform="translate(-16, 0)" opacity="0.6" />
    <path d="{foot_white}" fill="#FFFFFF" />
  </g>
</svg>"""

def make_concept_b():
    """
    Concept B: 'Italic Speed Z' (Dynamic Calligraphic Nib)
    Slanted 14 degrees forward, evoking stylus velocity and handwriting precision.
    """
    bg = make_bg(angle=-45, stripe_w=4, gap=12, stroke_color="#181E28", bg_color="#08090C")
    
    z_italic = (
        "M 74,58 "
        "L 206,58 "
        "L 194,100 "
        "L 146,100 "
        "L 96,154 "
        "L 186,154 "
        "L 174,198 "
        "L 46,198 "
        "L 58,154 "
        "L 108,154 "
        "L 158,100 "
        "L 86,100 "
        "Z"
    )
    
    white_wedge = (
        "M 46,198 "
        "L 98,198 "
        "L 114,154 "
        "L 58,154 "
        "Z"
    )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
{bg}
  <g id="mark">
    <!-- Orange Glitch Underlayer -->
    <path d="{z_italic}" fill="#FF944A" transform="translate(-18, -3)" />
    
    <!-- Cyan Forefront Layer -->
    <path d="{z_italic}" fill="#1DAEF5" />
    
    <!-- White Nib Terminal -->
    <path d="{white_wedge}" fill="#FF944A" transform="translate(-18, -3)" opacity="0.5" />
    <path d="{white_wedge}" fill="#FFFFFF" />
  </g>
</svg>"""

def make_concept_c():
    """
    Concept C: 'Glitch Shift / Scan-Tear Z' (CRT Glitch & Spliced Bar)
    Features an authentic horizontal CRT displacement slice across the diagonal spine,
    directly matching the frame-by-frame glitch loader from the app's splash!
    """
    bg = make_bg(angle=45, stripe_w=3.5, gap=10.5, stroke_color="#1C222E", bg_color="#090B0E")
    
    # Top plate
    plate_top = "M 56,60 L 202,60 L 202,100 L 152,100 L 126,126 L 88,126 L 114,100 L 56,100 Z"
    # Sliced displaced mid-segment (glitched leftward by 12px)
    mid_glitch = "M 88,126 L 126,126 L 102,150 L 64,150 Z"
    # Bottom plate
    plate_bottom = "M 64,150 L 102,150 L 102,154 L 198,154 L 198,196 L 52,196 L 52,156 L 64,150 Z"
    
    # Combined base
    z_c_base = (
        "M 56,60 "
        "L 204,60 "
        "L 204,98 "
        "L 152,98 "
        "L 126,124 "
        "L 142,124 "
        "L 142,136 "
        "L 114,136 "
        "L 96,156 "
        "L 200,156 "
        "L 200,196 "
        "L 52,196 "
        "L 52,158 "
        "L 104,158 "
        "L 122,136 "
        "L 78,136 "
        "L 78,124 "
        "L 110,124 "
        "L 136,98 "
        "L 56,98 "
        "Z"
    )
    
    white_foot = (
        "M 52,196 "
        "L 102,196 "
        "L 120,158 "
        "L 68,158 "
        "L 52,166 "
        "Z"
    )

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
{bg}
  <g id="mark">
    <!-- Orange CRT Glitch displacement -->
    <path d="{z_c_base}" fill="#FF944A" transform="translate(-16, 0)" />
    
    <!-- Horizontal scan tear glitch bar -->
    <rect x="42" y="122" width="168" height="14" fill="#FF944A" transform="translate(-12, 0)" opacity="0.85" />
    
    <!-- Primary Cyan Front Layer -->
    <path d="{z_c_base}" fill="#1DAEF5" />
    <rect x="54" y="122" width="168" height="14" fill="#1DAEF5" />
    
    <!-- White Accent Foot -->
    <path d="{white_foot}" fill="#FF944A" transform="translate(-16, 0)" opacity="0.6" />
    <path d="{white_foot}" fill="#FFFFFF" />
  </g>
</svg>"""

def main():
    os.makedirs(f"{OUT_DIR}/renders", exist_ok=True)
    with open(f"{OUT_DIR}/concept-a-symbol.svg", "w") as f:
        f.write(make_concept_a())
    with open(f"{OUT_DIR}/concept-b-symbol.svg", "w") as f:
        f.write(make_concept_b())
    with open(f"{OUT_DIR}/concept-c-symbol.svg", "w") as f:
        f.write(make_concept_c())
    print("Updated SVGs for concepts A, B, C.")

if __name__ == "__main__":
    main()
