"""Cuts the App Review walkthrough from two screen recordings made on an iPhone.

    python tools/make_review_video.py <recording 1.mp4> <recording 2.mp4> <output.mp4>

What it does: keeps the parts that show the app's main flow, speeds up typing and the AI waiting time
(and says so on screen), adds an English caption band above the app, a title card and a closing notes card,
and leaves out everything after the second recording's 49.8 s, where a notification banner and the lock
screen show private messages. No audio: the recordings are silent.
"""
import os
import shutil
import subprocess
import sys

V1, V2, OUT = [os.path.abspath(p) for p in sys.argv[1:4]]
WORK = os.path.join(os.path.dirname(OUT), '_work')
os.makedirs(WORK, exist_ok=True)
# The fonts are copied next to the work files: a drive letter's colon cannot go into a filter argument.
FONT = 'segoeuib.ttf'
FONT_REGULAR = 'segoeui.ttf'
for _f in (FONT, FONT_REGULAR):
    shutil.copy(os.path.join(os.environ.get('WINDIR', 'C:/Windows'), 'Fonts', _f), os.path.join(WORK, _f))
W, H, BAND = 720, 1556, 110
GREEN = '0x0f6b45'

# (video, start, end, speed, caption)
SEGMENTS = [
    (V2, 0.0, 21.0, 2.2, '1/9  First launch: set up a profile\nNo account or sign-in is needed'),
    (V2, 26.5, 38.0, 1.3, '2/9  Daily dashboard, expert tips\nand a suggested menu'),
    (V2, 38.5, 42.0, 1.0, '3/9  Progress: calories in and out,\nweight and burned calories'),
    (V1, 76.0, 84.5, 1.2, '4/9  Photograph a meal\nThe green circle marks what is analysed'),
    (V1, 85.0, 88.0, 1.0, '5/9  AI identifies the food\n(waiting time shortened in this video)'),
    (V1, 88.0, 108.0, 12.0, '5/9  AI identifies the food\n(waiting time shortened in this video)'),
    (V1, 108.5, 114.0, 1.0, '5/9  Review each dish, adjust it,\nthen add it to the food log'),
    (V1, 114.0, 133.0, 1.4, '6/9  Search foods and log them\nHealth warnings follow the declared conditions'),
    (V2, 42.0, 44.5, 1.0, '7/9  AI nutrition coach'),
    (V2, 44.5, 47.5, 6.0, '7/9  AI nutrition coach\n(waiting time shortened in this video)'),
    (V2, 47.5, 49.8, 1.0, '7/9  AI nutrition coach'),
    (V1, 45.5, 54.0, 1.0, '8/9  Optional Apple Health connection\nRead-only; the data stays on the device'),
    (V1, 54.0, 65.5, 1.15, '9/9  CaloCare Pro: auto-renewable subscriptions\nbought through Apple (StoreKit)'),
]

TITLE = ['CaloCare', '', 'Walkthrough for App Review', '', 'Screen recording on an iPhone', 'with the TestFlight build', '', 'No account or sign-in is needed']
END = ['Notes for App Review', '',
       '- AI features run on CaloCare\'s server', '  (Anthropic Claude). Results are estimates.', '',
       '- Free plan: 30 AI photos and 50 questions to', '  start, then 2 photos and 5 questions a day.', '  CaloCare Pro: up to 40 photos and 100 questions.', '',
       '- Apple Health data is read only and never', '  leaves the device.', '',
       '- AI waiting time is shortened in this video.', '', 'Contact: truongtumoc@gmail.com']


def run(cmd):
    done = subprocess.run(cmd, cwd=WORK, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    if done.returncode:
        sys.exit('ffmpeg failed: ' + ' '.join(cmd[:6]) + ' ...\n' + done.stderr[-1500:])


def textfile(name, text):
    with open(os.path.join(WORK, name), 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    return name


ENC = ['-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20', '-pix_fmt', 'yuv420p', '-r', '30', '-an']
parts = []

# title card
t = textfile('title.txt', '\n'.join(TITLE))
run(['ffmpeg', '-v', 'error', '-y', '-f', 'lavfi', '-i', f'color=c={GREEN}:s={W}x{H + BAND}:d=4:r=30',
     '-vf', f"drawtext=fontfile={FONT}:textfile={t}:fontsize=34:fontcolor=white:line_spacing=12:x=(w-text_w)/2:y=(h-text_h)/2,fade=t=out:st=3.5:d=0.5",
     *ENC, 'seg_title.mp4'])
parts.append('seg_title.mp4')

for n, (video, start, end, speed, caption) in enumerate(SEGMENTS):
    cap = textfile(f'cap{n}.txt', caption)
    vf = (f"trim=start={start}:end={end},setpts=(PTS-STARTPTS)/{speed},fps=30,scale={W}:{H},"
          f"pad={W}:{H + BAND}:0:{BAND}:color={GREEN},"
          f"drawtext=fontfile={FONT}:textfile={cap}:fontsize=26:fontcolor=white:line_spacing=6:x=(w-text_w)/2:y=({BAND}-text_h)/2")
    name = f'seg{n:02d}.mp4'
    run(['ffmpeg', '-v', 'error', '-y', '-i', video, '-vf', vf, *ENC, name])
    parts.append(name)

e = textfile('end.txt', '\n'.join(END))
run(['ffmpeg', '-v', 'error', '-y', '-f', 'lavfi', '-i', f'color=c={GREEN}:s={W}x{H + BAND}:d=7:r=30',
     '-vf', f"drawtext=fontfile={FONT_REGULAR}:textfile={e}:fontsize=27:fontcolor=white:line_spacing=10:x=48:y=(h-text_h)/2,fade=t=in:st=0:d=0.4",
     *ENC, 'seg_end.mp4'])
parts.append('seg_end.mp4')

listing = textfile('list.txt', ''.join(f"file '{p}'\n" for p in parts))
run(['ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', listing, '-c', 'copy', '-movflags', '+faststart', OUT])
shutil.rmtree(WORK, ignore_errors=True)
print(OUT)
