# Field Atlas timing: POCO X6 Neo 5G

## The phone

| | |
| --- | --- |
| Phone model | POCO X6 Neo 5G (2312FRAFDI) |
| Memory (Settings › About phone) | 12.0 GB RAM, shown as 12.0+4.0 GB under Detailed info and specs (Field Atlas shows 12.15 GB) |
| Android version | 15 (Xiaomi HyperOS 3.0.4.0) |
| Field Atlas version (as shown in More) | 1.4.3 |
| Model used | Qwen3.5 4B |
| Collections installed | Encyclopedia, Essentials |
| Battery level at the start | 58%, charging |

## How it was run

- Airplane mode on, Wi-Fi and mobile data off. No other app was opened during the run; background apps were not force-closed.
- Each question typed exactly as below, then **Start research**. Ask the first question once before you start timing,
  so the model is loaded.
- The time is the one shown on the finished answer, next to the clock icon.

## Times

| # | Question | Time shown | Finished? (yes / stopped / error) |
| --- | --- | --- | --- |
| 1 | When was the Eiffel Tower built, and how tall is it? | 2m 22s | yes |
| 2 | Why does the Moon look bigger near the horizon? | 2m 26s | yes |
| 3 | What is the difference between weather and climate? | 1m 14s | yes |
| 4 | Who was Hanaoka Seishu and what surgery did he perform? | 5m 27s | yes |
| 5 | How do I make stream water safe to drink when I am hiking? | Under 1s | yes |
| 6 | Summarize the main causes of the French Revolution. | 3m 47s | yes |
| 7 | my uncle dropped while carrying boxes up the stairs, eyes half open, just a snorty gasp every 8 seconds or so. do i start now | Under 1s | yes |
| 8 | Where can I find a hospital near the Colosseum in Rome? | 4s | yes |
| 9 | 450 km trip, the car uses 7 litres per 100 km and fuel costs 1.80 per litre. How much will the fuel cost? | Under 1s | yes |
| 10 | hello | 1m 43s | yes |

## Screenshots

Attach three screenshots to the pull request: question 1's finished answer with its time showing, **More** showing the
app version, and the phone's **Settings › About phone** showing its memory (RAM).

## Anything odd

- No crashes, and every answer finished. The app stayed in front for the whole run.
- Questions 5 and 7 opened a published guide, question 8 a map lookup and question 9 the calculator, so those four times do not measure the model.
- The questions were typed and started by a shell script on the phone over adb, one word at a time, and the text was checked against this table before **Start research**. While an answer ran, the script read the screen about every 20 seconds and sent a no-op key to keep the screen on, which may add a little load.
- The warm-up was question 1, asked with Wi-Fi still on: 4m 13s. Wi-Fi was then turned off and all 10 questions ran offline; the app showed **Offline**.
- The phone was charging for the whole run, from 58% to 67%. The battery read 40.0 °C at the end, warm but not hot. The run took 25 minutes, 16:33 to 16:58 UTC on 1 November 2026.
