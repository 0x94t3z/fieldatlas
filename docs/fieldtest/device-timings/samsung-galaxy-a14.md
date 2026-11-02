# Field Atlas timing: Samsung Galaxy A14

## The phone

| | |
| --- | --- |
| Phone model | Samsung Galaxy A14 (SM-A145F/DS) |
| Memory (Settings › About phone) | 4 GB RAM. About phone does not list it on this Samsung; Device care shows 4 GB (Field Atlas shows 3.86 GB) |
| Android version | 15 |
| Field Atlas version (as shown in More) | 1.4.5 |
| Model used | Qwen3.5 2B |
| Collections installed | Encyclopedia, Essentials |
| Battery level at the start | 95%, charging |

## How it was run

- Airplane mode on, Wi-Fi and mobile data off. No other app was opened during the run; cached background processes were cleared once before it started.
- Each question typed exactly as below, then **Start research**. Ask the first question once before you start timing,
  so the model is loaded.
- The time is the one shown on the finished answer, next to the clock icon.

## Times

| # | Question | Time shown | Finished? (yes / stopped / error) |
| --- | --- | --- | --- |
| 1 | When was the Eiffel Tower built, and how tall is it? | 11m 38s | yes |
| 2 | Why does the Moon look bigger near the horizon? | 3m 27s | yes |
| 3 | What is the difference between weather and climate? | 3m 39s | yes |
| 4 | Who was Hanaoka Seishu and what surgery did he perform? | 6m 35s | yes |
| 5 | How do I make stream water safe to drink when I am hiking? | 1s | yes |
| 6 | Summarize the main causes of the French Revolution. | 14m 41s | yes |
| 7 | my uncle dropped while carrying boxes up the stairs, eyes half open, just a snorty gasp every 8 seconds or so. do i start now | Under 1s | yes |
| 8 | Where can I find a hospital near the Colosseum in Rome? | 8s | yes |
| 9 | 450 km trip, the car uses 7 litres per 100 km and fuel costs 1.80 per litre. How much will the fuel cost? | Under 1s | yes |
| 10 | hello | Under 1s | yes |

## Screenshots

Attach three screenshots to the pull request: question 1's finished answer with its time showing, **More** showing the
app version, and the phone's **Settings › About phone** showing its memory (RAM).

## Anything odd

- No crashes, and every answer finished. The app stayed in front for the whole run.
- Questions 5 and 7 opened a published guide, question 8 used map data, question 9 the calculator, and question 10 a fixed app reply, so those five times do not measure the model. Questions 1, 2, 3, 4 and 6 do.
- Three of those five took longer than the "On slower phones an answer takes 2 to 5 minutes" line the app shows while it works: 6m 35s, 11m 38s and 14m 41s.
- The warm-up was question 1, in airplane mode: 10m 50s. The timed question 1 that followed was no faster, at 11m 38s.
- The questions were typed and started by a shell script on the phone over adb, one word at a time, and the text was checked against this table before each start. The script took no answer text or timing from anywhere but the finished screen.
- The phone was charging for the whole run, from 95% to 96%. The battery read 34.5 °C at the end, not warm. The run took 63 minutes, 06:50 to 07:53 UTC.
- With the model and both collections installed, Field Atlas used 29.54 GB, leaving 8.45 GB free of 52.95 GB.
