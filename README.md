# Slayer Block Lists

A RuneLite plugin that shows your slayer block list for **every** master in a side panel — not just
the one you're standing next to. Blocked creatures, masters and your current task are all buttons
that open the right OSRS Wiki page.

This plugin is largely designed for me for a personal project, but I can see this type of tool is likely useful to others as well. If you have any suggestions, please open an issue or PR.

If you enjoy my work, feel free to buy me a coffee:

<a href="https://www.buymeacoffee.com/kimpton" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: 41px !important;width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;" ></a>

## Features

- **Per-master block lists** — Turael, Mazchna, Vannaka, Chaeldar, Konar, Nieve, Duradel and
  Krystilia, each with all seven block slots.
- **Wiki links everywhere** — every blocked creature, the current task and each master name opens
  its OSRS Wiki page.
- **Empty & locked slots** — unlocked-but-unused slots are shown, and locked ones say what unlocks
  them (slot N needs N×50 quest points; the seventh slot needs the Elite Lumbridge & Draynor
  diary). Both can be hidden in the config.
- **Current task** — your active assignment with the remaining count, assigning master, and
  Konar's required kill location when applicable.
- **Slayer points & task streak** at the top.

Everything updates live as you play — block a task, skip one, or gain quest points and the panel
follows the same tick.

## Privacy

Reads game state only (vars and the game's own data tables). Nothing is written anywhere, and
nothing leaves your machine.

## Building

```sh
./gradlew run        # launch a development client with the plugin loaded
./gradlew shadowJar  # build a side-loadable jar
```

## AI Use

This tool was absolutely built with AI (claude) however I am also a software engineer by day.

- I have tried to guide the AI to produce code that is correct, readable, and maintainable in a way that I /think/ I would have written it myself, but I cannot guarantee that the code is perfect or free of bugs - "works for me".

## Contributors

- If you would like to submit a PR and you use AI, please be transparent about it in your PR description. I will not reject PRs that use AI, but I do want to know about it, as it helps me review the code.
- But please write your own PR or issue descriptions, and do not use AI to write it for you. I want to know your thoughts and reasoning, not an AI's; its too easy to throw up a bunch of waffle with AI.
