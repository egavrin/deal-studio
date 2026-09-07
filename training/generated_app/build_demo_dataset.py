#!/usr/bin/env python3
"""Builds reproducible UI-DSL and DEAL SFT sets for generated mini-app profiles."""

from __future__ import annotations

import argparse
import json
import random
from dataclasses import dataclass
from pathlib import Path


DEAL_SYSTEM = (
    "Generate DEAL generated-app profile source only. No prose, Markdown, JavaScript, "
    "ternary expressions, arrows or collection methods. Boolean OR is a single |. "
    "Use English UI text."
)

GRID_CONTRACT = """Profile: GRID.
ABI globals: title:string; status:string; primaryLabel:string; items:string[]; columns:int.
Actions: onItem(index:int):null; onPrimary():null.
Own all state and rules. Board games require occupied guard, turns, win/draw and full reset."""

CANVAS_CONTRACT = """Profile: REALTIME_CANVAS. Coordinates are integer scene units.
ABI globals: title:string; status:string; primaryLabel:string;
canvasWidth:int; canvasHeight:int; canvasBackground:string; continuousAnimation:boolean.
Use true only for periodic animation. Never declare parallel shape arrays.
Build a retained scene of at most 96 nodes with sceneClear and sceneRect, sceneRoundRect,
sceneCircle, sceneEllipse, sceneLine or sceneText. Creators take group,x,y,w,h,color and return handles;
sceneRoundRect adds radius, sceneText adds label. Mutate nodes with sceneSetPosition, sceneMove,
sceneSetSize, sceneSetColor, sceneSetStroke, sceneSetLabel, sceneSetVisible, sceneSetInteractive, sceneSetLayer,
sceneSetRotation and sceneSetCornerRadius. Iterate repeated groups with sceneCount and sceneAt.
Use sceneX/Y/W/H, sceneOverlaps, sceneContains, rectsOverlap, pointInRect or circlesOverlap for geometry.
Mark at least one visible pointer target with sceneSetInteractive(handle,true).
Actions: onTick(deltaMs:int):null; onPointer(x:int,y:int,phase:int):null; onPrimary():null.
Pointer phases: 0 down, 1 move, 2 up. sceneClear resets handles deterministically. Reset the full scene in onPrimary.
Implement movement, bounds, collisions, score/lives and win/lose state in DEAL."""

PROFILE_SELECTION_CONTRACT = f"""Select exactly one profile from the request. Use GRID for turn-based, board, counter, or cell apps.
Use REALTIME_CANVAS for continuous motion, physics, projectiles, dragging, Pong, Arkanoid, or action games.

{GRID_CONTRACT}

OR

{CANVAS_CONTRACT}
Never mix profile globals."""

REQUEST_TEMPLATE = """Build this interactive mini-app:
{request}

{contract}
Allowed: let/function/if/else/while/return/assignment, fixed arrays/index/.length, literals,
+ - * / % === !== && | < <= > >=. Core builtins: abs, min, max, clamp. REALTIME_CANVAS may
use only the scene builtins in its contract. End actions with return null. Keep loops bounded.
Complete source <=12000 chars."""

UI_PROMPT_TEMPLATE = """task=compact_widget_plan
intent=compose_widget
request={request}
layouts=column,row,stack,grid2,section
layout_props=gap:none|xs|sm|md|lg;align:start|center|end|stretch;padding:none|sm|md|lg;section.tone:plain|soft|accent|dark
required=text.heading(title=$title),text.status(status=$status),control.button(onPrimary=$onPrimary,primaryLabel=$primaryLabel)
interaction=surface.app(grid:tiles|outline|neon,gap:none|xs|sm|md|lg,frame:none|soft|bordered,ratio:scene|square|wide)
optional=text.label(text=$title|$status|$primaryLabel),decor.divider,decor.spacer
component_props=heading.style:display|title|compact;status.style:body|badge|caption;button.variant:filled|tonal|outline;button.icon:none|restart|play;tone:default|muted|primary|positive|warning|inverse;align:start|center|end
rules=compose_new_tree;required_once;surface_app_once;bindings_exact;max_16_components;max_depth_6
screen=compact"""

UI_PLANS = (
    "column(gap=sm)[row(gap=sm,align=center)[text.heading(title=$title,style=title),text.status(status=$status,style=badge,tone=primary,align=end)],surface.app(grid=tiles,gap=sm,frame=bordered,ratio=scene),control.button(onPrimary=$onPrimary,primaryLabel=$primaryLabel,variant=outline,icon=restart,size=compact)]",
    "section(tone=dark,padding=md,gap=sm)[text.heading(title=$title,style=display,tone=inverse),text.status(status=$status,style=caption,tone=inverse),surface.app(grid=outline,gap=xs,frame=soft,ratio=scene),decor.divider(tone=strong),control.button(onPrimary=$onPrimary,primaryLabel=$primaryLabel,variant=tonal,icon=restart)]",
    "column(gap=sm)[section(tone=accent,padding=sm,gap=xs)[text.heading(title=$title,style=compact),text.status(status=$status,style=badge,tone=positive)],stack(align=end)[surface.app(grid=neon,gap=sm,frame=bordered,ratio=wide),text.label(text=$status,style=badge,tone=inverse,align=end)],control.button(onPrimary=$onPrimary,primaryLabel=$primaryLabel,variant=filled,icon=play)]",
)

UI_STYLE_HINTS = (
    ("", " Use a clean default presentation.", " Keep the layout simple and clear."),
    (
        " Use a dark framed presentation.",
        " Use a dark HUD and framed content.",
        " Apply a dark theme with a compact status area.",
    ),
    (
        " Use an accent header and an overlay HUD.",
        " Add a colorful accent header with status over the app surface.",
        " Use an accent panel and overlay the live status.",
    ),
)


@dataclass(frozen=True)
class Family:
    name: str
    profile: str
    requests: tuple[str, ...]
    source: str
    split: str = "train"


TIC_TAC_TOE = r'''let title: string = "Tic-tac-toe";
let status: string = "Turn: X";
let primaryLabel: string = "New game";
let items: string[] = ["", "", "", "", "", "", "", "", ""];
let columns: int = 3;
let turn: string = "X";
let moves: int = 0;
let finished: boolean = false;
function onItem(index: int): null {
 if (finished | (items[index] !== "")) { return null; }
 items[index] = turn; moves = moves + 1;
 let won: boolean = ((items[0] === turn) && (items[1] === turn) && (items[2] === turn)) | ((items[3] === turn) && (items[4] === turn) && (items[5] === turn)) | ((items[6] === turn) && (items[7] === turn) && (items[8] === turn)) | ((items[0] === turn) && (items[3] === turn) && (items[6] === turn)) | ((items[1] === turn) && (items[4] === turn) && (items[7] === turn)) | ((items[2] === turn) && (items[5] === turn) && (items[8] === turn)) | ((items[0] === turn) && (items[4] === turn) && (items[8] === turn)) | ((items[2] === turn) && (items[4] === turn) && (items[6] === turn));
 if (won) { status = "Winner: " + turn; finished = true; } else if (moves === 9) { status = "Draw"; finished = true; } else { if (turn === "X") { turn = "O"; } else { turn = "X"; } status = "Turn: " + turn; }
 return null;
}
function onPrimary(): null { items = ["", "", "", "", "", "", "", "", ""]; status = "Turn: X"; turn = "X"; moves = 0; finished = false; return null; }'''

TOGGLE_GRID = r'''let title: string = "Light grid";
let status: string = "Tap any cell";
let primaryLabel: string = "Reset";
let items: string[] = ["○", "○", "○", "○", "○", "○", "○", "○", "○"];
let columns: int = 3;
function onItem(index: int): null { if (items[index] === "○") { items[index] = "●"; } else { items[index] = "○"; } status = "Grid updated"; return null; }
function onPrimary(): null { items = ["○", "○", "○", "○", "○", "○", "○", "○", "○"]; status = "Tap any cell"; return null; }'''

PIXEL_CANVAS = r'''let title: string = "Pixel canvas";
let status: string = "Tap cells to paint";
let primaryLabel: string = "Clear";
let items: string[] = ["", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""];
let columns: int = 4;
function onItem(index: int): null { if (items[index] === "") { items[index] = "●"; } else { items[index] = ""; } status = "Canvas updated"; return null; }
function onPrimary(): null { items = ["", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""]; status = "Tap cells to paint"; return null; }'''

PONG = r'''let title: string = "Generated Pong";
let status: string = "Drag the left paddle"; let primaryLabel: string = "Restart";
let canvasWidth: int = 1000; let canvasHeight: int = 600; let canvasBackground: string = "#0F172A"; let continuousAnimation: boolean = true;
let player: int = 0; let opponent: int = 0; let ball: int = 0;
let ballX: int = 485; let ballY: int = 285; let velocityX: int = 6; let velocityY: int = 4;
function buildScene(): null {
 sceneClear(); player = sceneRoundRect("paddles",30,250,30,100,"#38BDF8",8); sceneSetInteractive(player,true);
 opponent = sceneRoundRect("paddles",940,250,30,100,"#F8FAFC",8);
 ball = sceneCircle("ball",ballX,ballY,30,30,"#F8FAFC"); sceneSetLayer(ball,2);
 sceneLine("court",499,0,0,600,"#334155"); return null;
}
buildScene();
function onTick(deltaMs: int): null {
 ballX = ballX + velocityX * deltaMs / 16; ballY = ballY + velocityY * deltaMs / 16;
 if ((ballY <= 0) | (ballY >= 570)) { velocityY = -velocityY; }
 if ((ballX <= 60) | (ballX >= 910)) { velocityX = -velocityX; }
 ballX = clamp(ballX,0,970); ballY = clamp(ballY,0,570); sceneSetPosition(ball,ballX,ballY); return null;
}
function onPointer(x: int, y: int, phase: int): null { sceneSetPosition(player,30,clamp(y-50,0,500)); status = "Paddle ready"; return null; }
function onPrimary(): null { ballX=485; ballY=285; velocityX=6; velocityY=4; status="Drag the left paddle"; buildScene(); return null; }'''

ARKANOID = r'''let title: string = "Generated Arkanoid";
let status: string = "Drag the paddle"; let primaryLabel: string = "Restart";
let canvasWidth: int = 1000; let canvasHeight: int = 700; let canvasBackground: string = "#08111F"; let continuousAnimation: boolean = true;
let paddle: int = 0; let ball: int = 0; let ballX: int = 485; let ballY: int = 585;
let velocityX: int = 5; let velocityY: int = -6; let bricks: int = 8; let lives: int = 3;
function buildScene(): null {
 sceneClear(); paddle=sceneRoundRect("paddle",400,640,200,24,"#38BDF8",12); sceneSetInteractive(paddle,true);
 ball=sceneCircle("ball",ballX,ballY,30,30,"#F8FAFC"); sceneSetLayer(ball,2);
 let row:int=0; let column:int=0; let brick:int=0; while (row<2) { column=0; while (column<4) {
  brick=sceneRoundRect("bricks",90+column*210,90+row*80,180,48,"#F97316",10);
  if (row===1) { sceneSetColor(brick,"#22C55E"); } column=column+1;
 } row=row+1; } return null;
}
buildScene();
function onTick(deltaMs:int):null {
 ballX=ballX+velocityX*deltaMs/16; ballY=ballY+velocityY*deltaMs/16;
 if ((ballX<=0)|(ballX>=970)) { velocityX=-velocityX; } if (ballY<=0) { velocityY=abs(velocityY); }
 sceneSetPosition(ball,ballX,ballY);
 if (sceneOverlaps(ball,paddle)) { velocityY=-abs(velocityY); }
 let i:int=0; let brick:int=0; while (i<sceneCount("bricks")) { brick=sceneAt("bricks",i);
  if (sceneVisible(brick)&&sceneOverlaps(ball,brick)) { sceneSetVisible(brick,false); velocityY=-velocityY; bricks=bricks-1; status="Bricks left: "+bricks; }
  i=i+1;
 }
 if (ballY>680) { lives=lives-1; ballX=485; ballY=585; velocityY=-6; sceneSetPosition(ball,ballX,ballY); status="Lives: "+lives; }
 if (bricks===0) { status="You cleared the board"; velocityX=0; velocityY=0; } return null;
}
function onPointer(x:int,y:int,phase:int):null { sceneSetPosition(paddle,clamp(x-100,0,800),640); status="Paddle ready"; return null; }
function onPrimary():null { ballX=485; ballY=585; velocityX=5; velocityY=-6; bricks=8; lives=3; status="Drag the paddle"; buildScene(); return null; }'''

TANK_DUEL = r'''let title:string="Pocket Tanks"; let status:string="Drag to move and tap to fire"; let primaryLabel:string="Restart";
let canvasWidth:int=1000; let canvasHeight:int=600; let canvasBackground:string="#172033"; let continuousAnimation:boolean=true;
let player:int=0; let enemy:int=0; let shell:int=0; let enemyDirection:int=3; let shellVelocity:int=0;
function buildScene():null { sceneClear(); player=sceneRoundRect("tanks",100,480,90,60,"#38BDF8",12); sceneSetInteractive(player,true); enemy=sceneRoundRect("tanks",820,100,90,60,"#F97316",12); shell=sceneCircle("shell",135,455,20,20,"#BAE6FD"); sceneSetVisible(shell,false); return null; }
buildScene();
function onTick(deltaMs:int):null { sceneMove(enemy,enemyDirection*deltaMs/16,0); if ((sceneX(enemy)<=550)|(sceneX(enemy)>=900)) { enemyDirection=-enemyDirection; } if (shellVelocity!==0) { sceneMove(shell,0,shellVelocity*deltaMs/16); } if (sceneVisible(shell)&&sceneOverlaps(shell,enemy)) { status="Direct hit"; shellVelocity=0; sceneSetVisible(shell,false); } return null; }
function onPointer(x:int,y:int,phase:int):null { sceneSetPosition(player,clamp(x-45,0,910),clamp(y-30,300,540)); if (phase===0) { sceneSetPosition(shell,sceneX(player)+35,sceneY(player)-25); sceneSetVisible(shell,true); shellVelocity=-10; status="Shell fired"; } return null; }
function onPrimary():null { enemyDirection=3; shellVelocity=0; status="Drag to move and tap to fire"; buildScene(); return null; }'''

BOUNCING_TARGET = r'''let title:string="Bounce Lab"; let status:string="Drag the target"; let primaryLabel:string="Restart";
let canvasWidth:int=800; let canvasHeight:int=500; let canvasBackground:string="#111827"; let continuousAnimation:boolean=true;
let orb:int=0; let target:int=0; let velocityX:int=7; let velocityY:int=5;
function buildScene():null { sceneClear(); orb=sceneCircle("orb",100,100,44,44,"#F8FAFC"); target=sceneCircle("target",650,350,72,72,"#22C55E"); sceneSetInteractive(target,true); sceneSetStroke(target,"#86EFAC",4); sceneLine("ground",0,450,800,0,"#334155"); return null; }
buildScene();
function onTick(deltaMs:int):null { sceneMove(orb,velocityX*deltaMs/16,velocityY*deltaMs/16); if ((sceneX(orb)<=0)|(sceneX(orb)>=756)) { velocityX=-velocityX; } if ((sceneY(orb)<=0)|(sceneY(orb)>=406)) { velocityY=-velocityY; } sceneSetPosition(orb,clamp(sceneX(orb),0,756),clamp(sceneY(orb),0,406)); return null; }
function onPointer(x:int,y:int,phase:int):null { sceneSetPosition(target,clamp(x-36,0,728),clamp(y-36,0,428)); status="Target moved"; return null; }
function onPrimary():null { velocityX=7; velocityY=5; status="Drag the target"; buildScene(); return null; }'''

SPACE_SHOOTER = r'''let title:string="Orbit Defender"; let status:string="Drag the ship and tap to fire"; let primaryLabel:string="Restart";
let canvasWidth:int=900; let canvasHeight:int=600; let canvasBackground:string="#020617"; let continuousAnimation:boolean=true;
let ship:int=0; let enemy:int=0; let projectile:int=0; let scoreLabel:int=0; let enemyDirection:int=5; let projectileSpeed:int=0; let score:int=0;
function buildScene():null { sceneClear(); ship=sceneRoundRect("ship",410,520,80,46,"#38BDF8",16); sceneSetInteractive(ship,true); enemy=sceneRoundRect("enemy",390,80,80,46,"#F43F5E",16); projectile=sceneCircle("projectile",438,500,18,18,"#F8FAFC"); sceneSetVisible(projectile,false); scoreLabel=sceneText("hud",24,24,160,30,"#94A3B8","Score 0"); return null; }
buildScene();
function onTick(deltaMs:int):null { sceneMove(enemy,enemyDirection*deltaMs/16,0); if ((sceneX(enemy)<=20)|(sceneX(enemy)>=800)) { enemyDirection=-enemyDirection; } if (projectileSpeed!==0) { sceneMove(projectile,0,projectileSpeed*deltaMs/16); } if (sceneVisible(projectile)&&sceneOverlaps(projectile,enemy)) { score=score+1; sceneSetLabel(scoreLabel,"Score "+score); projectileSpeed=0; sceneSetVisible(projectile,false); status="Target hit"; } if (sceneY(projectile)<0) { projectileSpeed=0; sceneSetVisible(projectile,false); } return null; }
function onPointer(x:int,y:int,phase:int):null { sceneSetPosition(ship,clamp(x-40,0,820),520); if (phase===0) { sceneSetPosition(projectile,sceneX(ship)+31,490); sceneSetVisible(projectile,true); projectileSpeed=-12; status="Projectile launched"; } return null; }
function onPrimary():null { enemyDirection=5; projectileSpeed=0; score=0; status="Drag the ship and tap to fire"; buildScene(); return null; }'''

RUNNER = r'''let title:string="Neon Runner"; let status:string="Tap to jump"; let primaryLabel:string="Restart";
let canvasWidth:int=900; let canvasHeight:int=500; let canvasBackground:string="#0B1020"; let continuousAnimation:boolean=true;
let runner:int=0; let obstacle:int=0; let scoreLabel:int=0; let verticalVelocity:int=0; let obstacleSpeed:int=8; let score:int=0; let running:boolean=true;
function buildScene():null { sceneClear(); runner=sceneRoundRect("runner",120,360,56,70,"#22D3EE",12); sceneSetInteractive(runner,true); obstacle=sceneRoundRect("obstacle",760,350,70,80,"#FB7185",10); sceneLine("ground",0,430,900,0,"#475569"); scoreLabel=sceneText("hud",24,24,160,30,"#E2E8F0","Score 0"); return null; }
buildScene();
function onTick(deltaMs:int):null { if (running) { sceneMove(obstacle,-obstacleSpeed*deltaMs/16,0); if (sceneX(obstacle)<-70) { sceneSetPosition(obstacle,900,350); score=score+1; sceneSetLabel(scoreLabel,"Score "+score); } verticalVelocity=verticalVelocity+deltaMs/28; sceneMove(runner,0,verticalVelocity*deltaMs/16); if (sceneY(runner)>=360) { sceneSetPosition(runner,120,360); verticalVelocity=0; } if (sceneOverlaps(runner,obstacle)) { running=false; status="Run over"; } } return null; }
function onPointer(x:int,y:int,phase:int):null { if ((phase===0)&&running&&(sceneY(runner)>=360)) { verticalVelocity=-16; status="Jump"; } return null; }
function onPrimary():null { verticalVelocity=0; score=0; running=true; status="Tap to jump"; buildScene(); return null; }'''


FAMILIES = (
    Family("tic_tac_toe", "GRID", ("Build a two-player tic-tac-toe game", "Create noughts and crosses with a reset button", "Make a turn-based 3x3 X and O board"), TIC_TAC_TOE),
    Family("toggle_grid", "GRID", ("Build a 3x3 light toggle grid", "Make nine cells switch on and off when tapped", "Create a touch-controlled lights board"), TOGGLE_GRID),
    Family("pixel_canvas", "GRID", ("Build a 4x4 tap-to-paint pixel canvas", "Make a sixteen-cell drawing board", "Create a grid where touched pixels toggle"), PIXEL_CANVAS, "validation"),
    Family("pong", "REALTIME_CANVAS", ("Build a responsive two-player Pong game", "Create touch-controlled Pong", "Make a realtime paddle and ball game"), PONG),
    Family("arkanoid", "REALTIME_CANVAS", ("Build an Arkanoid game with bricks, lives, and touch paddle control", "Create a realtime brick breaker", "Make touch-controlled Breakout with collisions"), ARKANOID),
    Family("bouncing_target", "REALTIME_CANVAS", ("Build a bouncing ball target lab", "Create a realtime draggable target with a moving orb", "Make a touch target and bouncing ball toy"), BOUNCING_TARGET),
    Family("space_shooter", "REALTIME_CANVAS", ("Build a touch-controlled space shooter", "Create a moving enemy and projectile game", "Make a realtime spaceship target game"), SPACE_SHOOTER),
    Family("tank_duel", "REALTIME_CANVAS", ("Build a small touch-controlled tank duel", "Create a realtime tank and projectile game", "Make a pocket tanks mini-game"), TANK_DUEL),
    Family("runner", "REALTIME_CANVAS", ("Build a tap-to-jump endless runner", "Create a realtime runner with obstacles and score", "Make a jumping runner mini-game"), RUNNER, "test"),
)


def deal_record(family: Family, request: str) -> dict:
    return {"messages": [
        {"role": "system", "content": DEAL_SYSTEM},
        {"role": "user", "content": REQUEST_TEMPLATE.format(request=request, contract=PROFILE_SELECTION_CONTRACT)},
        {"role": "assistant", "content": family.source},
    ], "family": family.name, "profile": family.profile}


def ui_record(family: Family, request: str, plan_index: int) -> dict:
    output = UI_PLANS[plan_index]
    prompt = UI_PROMPT_TEMPLATE.format(
        request=request.replace(chr(10), " ").replace(chr(13), " ")[:180]
    )
    return {
        "messages": [
            {"role": "user", "content": prompt},
            {"role": "assistant", "content": output},
        ],
        "family": family.name,
        "profile": family.profile,
    }


def write_rows(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as output:
        for row in rows:
            output.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True, help="DEAL dataset directory")
    parser.add_argument("--ui-output", type=Path, help="Optional UI DSL dataset directory")
    parser.add_argument("--seed", type=int, default=20260825)
    parser.add_argument("--repetitions", type=int, default=6)
    args = parser.parse_args()
    rng = random.Random(args.seed)
    deal_splits: dict[str, list[dict]] = {"train": [], "validation": [], "test": []}
    ui_splits: dict[str, list[dict]] = {"train": [], "validation": [], "test": []}
    for family in FAMILIES:
        for repetition in range(args.repetitions):
            for request in family.requests:
                suffix = "" if repetition == 0 else f" Variant {repetition + 1}."
                deal_splits[family.split].append(deal_record(family, request + suffix))
        ui_repetitions = (
            args.repetitions * 5 // 2
            if family.split == "train" and family.profile == "GRID"
            else args.repetitions
        )
        for repetition in range(ui_repetitions):
            for request in family.requests:
                plan_index = repetition % len(UI_STYLE_HINTS)
                suffix = "" if repetition < len(UI_STYLE_HINTS) else f" Variant {repetition + 1}."
                aliases = UI_STYLE_HINTS[plan_index]
                style_hint = aliases[(repetition // len(UI_STYLE_HINTS)) % len(aliases)]
                styled_request = request + suffix + style_hint
                ui_splits[family.split].append(ui_record(family, styled_request, plan_index))
    for rows in deal_splits.values():
        rng.shuffle(rows)
    for rows in ui_splits.values():
        rng.shuffle(rows)
    args.output.mkdir(parents=True, exist_ok=True)
    for split, rows in deal_splits.items():
        filename = "valid" if split == "validation" else split
        write_rows(args.output / f"{filename}.jsonl", rows)
    if args.ui_output:
        args.ui_output.mkdir(parents=True, exist_ok=True)
        for split, rows in ui_splits.items():
            filename = "valid" if split == "validation" else split
            write_rows(args.ui_output / f"{filename}.jsonl", rows)
    print(json.dumps({
        "deal": {split: len(rows) for split, rows in deal_splits.items()},
        "ui": {split: len(rows) for split, rows in ui_splits.items()},
        "held_out": {"validation": "pixel_canvas", "test": "runner"},
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
