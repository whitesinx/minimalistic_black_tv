# minimalistic_black_tv

My Xiaomi Mi TV 4A Pro (MiTV-AXSO0, Android TV 9), stripped of PatchWall, ads and telemetry, with a
home screen that is pure black and nothing else: white monochrome icons, a clock, and out of the way.

> **Credits.** This is a fork of [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by
> [Gathin](https://github.com/Gathin23), MIT licensed. The debloat tools, the restore logic and the
> launcher it is built on are their work. What is mine is the look and a handful of features for this
> one TV. If you want the general-purpose version, with lists for 170+ TV models, use the original
> and give it a star.

## What it looks like

| Part | What it is |
|---|---|
| **Pure black** | No wallpaper, no glow, no artwork. Black, white, and grey for what is not focused. |
| **A centred dock** | White monochrome app icons in one row, centred low on the screen, no band or glass box. Focus shows as an icon going from dim to full white. |
| **The clock centred at the top** | Greeting, time and date in the middle of the top edge. |
| **A small stats readout** | Memory, network speed and free storage as a quiet infographic at the top left. |
| **A plain-text top bar** | Play Store and Debugging toggles, Settings and Power, as words, not pills. |
| **A prayer screensaver** | A 24-hour clock on black with today's prayer times (Asia/Jakarta), kept still. |
| **A 1440p-like scale** | Everything a little smaller than stock, so the screen feels less crowded. |
| **Remote extras** | Spare remote buttons toggle WiZ lights on the local network, and volume moves in steps of 5. |

All of it is set in [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml).

## Setting it up

You need a computer on the same Wi-Fi as the TV.

**On the TV:** Settings → Device Preferences → About → press OK on **Build** seven times, then
Developer options → **USB debugging** on (and Network debugging if it is there).

**On the computer:**

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                  # your TV's IP address

tools/venv/bin/python -I tools/tv.py --probe                  # check the connection: model, Android, firmware
tools/disable.sh examples/xiaomi-mitv-axso0/debloat.txt       # disable PatchWall, ads and telemetry
launcher/build-and-install.sh                                 # build the launcher and make it Home
```

Accept "Allow USB debugging?" on the TV the first time. Or paste [`PROMPT.md`](PROMPT.md) into a coding
assistant with shell access and let it do the steps for you, checking each one with screenshots.

Nothing is uninstalled: packages are disabled with `pm disable-user`, and `disable.sh` writes down
what the TV looked like before it changes anything.

## Undoing it

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/xiaomi-mitv-axso0/debloat.txt
```

That re-enables what the list disabled and gives Home back to the stock launcher. The launcher stays
installed; remove it from the TV's Settings → Apps if you want it gone.

When you are done, switch USB and Network debugging off again: port 5555 is plaintext, and anyone on
your network could ask for a shell.

## Tools

| Tool | What it does |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Pure-Python ADB client, for when macOS blocks the stock `adb` binary. |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Apply or undo a debloat list. |
| [`tools/check.py`](tools/check.py) | Shows what a firmware update re-enabled; `--fix` puts it back. |
| [`tools/theme.sh`](tools/theme.sh) | Pushes a theme from [`themes/`](themes/) without a rebuild. |
| [`tools/adb-closed.py`](tools/adb-closed.py) | Confirms the TV's ADB port is closed again. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Builds, installs, switches Home, and rolls back if Home does not land. |

More detail, including Windows and other TVs, is in [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md)
and in the [original README](https://github.com/Gathin23/cleanyourtv#readme).

## Other TVs

This repo is tuned for one TV. The launcher and tools still work on most Android TV and Google TV sets,
and [`examples/`](examples/) keeps the contributed debloat lists from upstream, but for anything other
than a MiTV-AXSO0 you will be better served by [CleanYourTV](https://github.com/Gathin23/cleanyourtv).

## Licence

MIT, see [`LICENSE`](LICENSE). Original work © Gathin, changes in this fork © whitesinx. The Outfit
typeface is under the SIL Open Font License, see `launcher/FONT-LICENSE-OFL.txt`.
