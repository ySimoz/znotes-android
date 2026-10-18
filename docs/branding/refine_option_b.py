#!/usr/bin/env python3
"""
Refine Option B with 3 calibrated stroke-balance variants:
B1: 1:1 Uniform Ratio — 36px horizontal bars, 36px perpendicular diagonal.
B2: Muscular Chisel — 38px horizontal bars, 42px dominant diagonal spine.
B3: Sleek Pen Stroke — 32px slim horizontal bars, 35px diagonal stem.
"""

import os
import math
import subprocess

OUT_DIR = "docs/branding"

def make_bg(pat_id="pat_b", angle=-45, stripe_w=4, gap=12, stroke_color="#181E29", bg_color="#08090C"):
    step = stripe_w + gap
    return f"""  <defs>
    <pattern id="{pat_id}" width="{step}" height="{step}" patternTransform="rotate({angle})" patternUnits="userSpaceOnUse">
      <rect width="{step}" height="{step}" fill="{bg_color}" />
      <line x1="0" y1="0" x2="0" y2="{step}" stroke="{stroke_color}" stroke-width="{stripe_w}" />
    </pattern>
  </defs>
  <rect width="256" height="256" fill="url(#{pat_id})" rx="44" />"""

def make_svg(z_path, white_wedge, pat_id, offset_x=-16, offset_y=-3):
    bg = make_bg(pat_id=pat_id)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
{bg}
  <g id="mark">
    <!-- Orange Glitch Underlayer -->
    <path d="{z_path}" fill="#FF944A" transform="translate({offset_x}, {offset_y})" />
    
    <!-- Cyan Primary Layer -->
    <path d="{z_path}" fill="#1DAEF5" />
    
    <!-- White Accent Wedge -->
    <path d="{white_wedge}" fill="#FF944A" transform="translate({offset_x}, {offset_y})" opacity="0.5" />
    <path d="{white_wedge}" fill="#FFFFFF" />
  </g>
</svg>"""

def make_lockup(symbol_svg_inner):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 768 256" width="768" height="256">
  <rect width="768" height="256" fill="#0A0B0E" />
  <g transform="translate(32, 16) scale(0.875)">
    {symbol_svg_inner}
  </g>
  <g transform="translate(280, 160)" font-family="system-ui, -apple-system, sans-serif" font-weight="800">
    <text x="0" y="0" font-size="108" fill="#FF944A" dx="-5" dy="0" letter-spacing="-1">z</text>
    <text x="0" y="0" font-size="108" fill="#1DAEF5" letter-spacing="-1">z</text>
    <text x="64" y="0" font-size="108" fill="#FFFFFF" letter-spacing="-1">Note</text>
    <text x="6" y="38" font-size="20" fill="#8892A4" font-weight="500" letter-spacing="4">HANDWRITING NOTEBOOK</text>
  </g>
</svg>"""

# -----------------------------------------------------------------
# Variant B1: 1:1 Uniform Weight (36px horizontals, 36px diagonal)
# -----------------------------------------------------------------
# Horizontal bars: y=60..96 (H=36) and y=160..196 (H=36)
# Gap: 160 - 96 = 64px.
# Diagonal: top-right at (188, 96), bottom-left at (68, 160).
# dx = 68 - 188 = -120, dy = 64. diag_len = 136. sin_theta = 64/136 = 0.470.
# For 36px perpendicular: dx_perp = 36 / 0.470 = 76.5 px horizontally!
# Top-left of diagonal meets top bar at: 188 - 76.5 = 111.5, y=96.
# Bottom-right of diagonal meets bottom bar at: 68 + 76.5 = 144.5, y=160.
z_b1 = (
    "M 74,60 "
    "L 204,60 "
    "L 194,96 "
    "L 188,96 "
    "L 68,160 "
    "L 186,160 "
    "L 174,196 "
    "L 46,196 "
    "L 56,160 "
    "L 144,160 "
    "L 204,96 "
    "Z"
)
# Wait, let's trace properly:
# Outer perimeter of Z:
# 1. (74, 60) -> (204, 60)
# 2. Down right edge of top bar to (194, 96)
# 3. Inner corner under top bar: (112, 96)
# 4. Down left edge of diagonal to (56, 160)
# 5. Down left edge of bottom bar to (46, 196)
# 6. Across bottom bar to (174, 196)
# 7. Up right edge of bottom bar to (184, 160)
# 8. Inner corner above bottom bar: (144, 160)
# 9. Up right edge of diagonal to (188, 96) -> wait, (188, 96) connects to top-right!
z_b1_fixed = (
    "M 74,60 "
    "L 206,60 "
    "L 194,96 "
    "L 176,96 "
    "L 110,160 "
    "L 184,160 "
    "L 174,196 "
    "L 46,196 "
    "L 58,160 "
    "L 124,160 "
    "L 188,96 "
    "L 86,96 "
    "Z"
)
# Let's check perpendicular thickness:
# Diagonal right edge: from (176, 96) to (110, 160) -> dx = -66, dy = 64
# Diagonal left edge: from (124, 160) to (188, 96)? Wait, that goes the other way!
# Left edge goes from (86, 96) to (58, 160)!
# dx = 58 - 86 = -28, dy = 64. Not parallel!

# Let's write an exact geometric polygon builder that guarantees 100% parallel edges!
def build_z_polygon(x_tl, x_tr, y_top_0, y_top_1, x_bl, x_br, y_bot_0, y_bot_1, w_diag_perp, italic_dx=12):
    # Top bar:
    # Top edge: (x_tl, y_top_0) to (x_tr, y_top_0)
    # Right edge slanted: (x_tr, y_top_0) to (x_tr - italic_dx, y_top_1)
    
    # Bottom bar:
    # Bottom edge: (x_bl, y_bot_1) to (x_br, y_bot_1)
    # Left edge slanted: (x_bl + italic_dx, y_bot_0) to (x_bl, y_bot_1)
    # Right edge slanted: (x_br, y_bot_1) to (x_br + italic_dx, y_bot_0)
    
    dy_gap = y_bot_0 - y_top_1
    
    # Let diagonal right edge go from (x_diag_tr, y_top_1) to (x_diag_br, y_bot_0)
    # Let x_diag_tr = x_tr - italic_dx - 10
    # Let x_diag_br = x_bl + 72
    # Slope vector:
    dx_diag = -64
    diag_len = math.hypot(dx_diag, dy_gap)
    sin_theta = dy_gap / diag_len
    dx_horiz = w_diag_perp / sin_theta
    
    # Right diagonal line:
    x_d_tr = x_tr - italic_dx - 8
    x_d_br = x_d_tr + dx_diag
    
    # Left diagonal line (shifted left by dx_horiz):
    x_d_tl = x_d_tr - dx_horiz
    x_d_bl = x_d_br - dx_horiz
    
    # Check bounds
    path = (
        f"M {x_tl},{y_top_0} "
        f"L {x_tr},{y_top_0} "
        f"L {x_tr - italic_dx},{y_top_1} "
        f"L {x_d_tr},{y_top_1} "
        f"L {x_d_br},{y_bot_0} "
        f"L {x_br + italic_dx},{y_bot_0} "
        f"L {x_br},{y_bot_1} "
        f"L {x_bl},{y_bot_1} "
        f"L {x_bl + italic_dx},{y_bot_0} "
        f"L {x_d_bl},{y_bot_0} "
        f"L {x_d_tl},{y_top_1} "
        f"L {x_tl - italic_dx},{y_top_1} "
        f"Z"
    )
    
    # White wedge at bottom-left terminal
    white_wedge = (
        f"M {x_bl},{y_bot_1} "
        f"L {x_bl + 52},{y_bot_1} "
        f"L {x_bl + 52 + italic_dx},{y_bot_0} "
        f"L {x_bl + italic_dx},{y_bot_0} "
        f"Z"
    )
    
    return path, white_wedge

def main():
    os.makedirs(f"{OUT_DIR}/renders", exist_ok=True)
    
    # -------------------------------------------------------------
    # B1: Uniform 1:1 Harmony (36px bars, 36px diagonal)
    # -------------------------------------------------------------
    path_b1, white_b1 = build_z_polygon(
        x_tl=74, x_tr=206, y_top_0=60, y_top_1=96,
        x_bl=48, x_br=176, y_bot_0=160, y_bot_1=196,
        w_diag_perp=36, italic_dx=12
    )
    svg_b1 = make_svg(path_b1, white_b1, "pat_b1", offset_x=-16, offset_y=-3)
    with open(f"{OUT_DIR}/concept-b1-symbol.svg", "w") as f: f.write(svg_b1)
    with open(f"{OUT_DIR}/concept-b1-lockup.svg", "w") as f: f.write(make_lockup(svg_b1))

    # -------------------------------------------------------------
    # B2: Muscular Chisel (38px bars, 42px dominant diagonal)
    # -------------------------------------------------------------
    path_b2, white_b2 = build_z_polygon(
        x_tl=70, x_tr=208, y_top_0=58, y_top_1=96,
        x_bl=44, x_br=180, y_bot_0=158, y_bot_1=196,
        w_diag_perp=42, italic_dx=12
    )
    svg_b2 = make_svg(path_b2, white_b2, "pat_b2", offset_x=-17, offset_y=-3)
    with open(f"{OUT_DIR}/concept-b2-symbol.svg", "w") as f: f.write(svg_b2)
    with open(f"{OUT_DIR}/concept-b2-lockup.svg", "w") as f: f.write(make_lockup(svg_b2))

    # -------------------------------------------------------------
    # B3: Sleek Pen Stroke (32px bars, 34px diagonal)
    # -------------------------------------------------------------
    path_b3, white_b3 = build_z_polygon(
        x_tl=76, x_tr=204, y_top_0=64, y_top_1=96,
        x_bl=50, x_br=174, y_bot_0=164, y_bot_1=196,
        w_diag_perp=34, italic_dx=12
    )
    svg_b3 = make_svg(path_b3, white_b3, "pat_b3", offset_x=-15, offset_y=-3)
    with open(f"{OUT_DIR}/concept-b3-symbol.svg", "w") as f: f.write(svg_b3)
    with open(f"{OUT_DIR}/concept-b3-lockup.svg", "w") as f: f.write(make_lockup(svg_b3))

    print("Generated B1, B2, B3 SVGs.")

if __name__ == "__main__":
    main()
