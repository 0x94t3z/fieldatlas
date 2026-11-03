# Field Atlas timing: LG V60 ThinQ

## The phone

| | |
| --- | --- |
| Phone model | LG V60 ThinQ 5G (LM-V600TM) |
| Memory (Settings › About phone) | 8 GB physical RAM (Settings › Memory shows 8.0 GB; Field Atlas shows 8.03 GB) |
| Android version | 13 |
| Field Atlas version (as shown in More) | 1.4.5 |
| Model used | Qwen3.5 4B |
| Collections installed | Encyclopedia, Essentials |
| Battery level at the start | 80% |

## How it was run

- Airplane mode on, Wi-Fi and mobile data off, no other apps open during the run.
- Each question typed exactly as below, then **Start research**. The first question was asked once
  before timing started, so the model was loaded.
- The time is the one shown on the finished answer, next to the clock icon.

## Times

| # | Question | Time shown | Finished? (yes / stopped / error) |
| --- | --- | --- | --- |
| 1 | When was the Eiffel Tower built, and how tall is it? | 3m 3s | yes |
| 2 | Why does the Moon look bigger near the horizon? | 1m 31s | yes |
| 3 | What is the difference between weather and climate? | 51s | yes |
| 4 | Who was Hanaoka Seishu and what surgery did he perform? | 3m 15s | yes |
| 5 | How do I make stream water safe to drink when I am hiking? | Under 1s | yes |
| 6 | Summarize the main causes of the French Revolution. | 2m 18s | yes |
| 7 | my uncle dropped while carrying boxes up the stairs, eyes half open, just a snorty gasp every 8 seconds or so. do i start now | Under 1s | yes |
| 8 | Where can I find a hospital near the Colosseum in Rome? | 4s | yes |
| 9 | 450 km trip, the car uses 7 litres per 100 km and fuel costs 1.80 per litre. How much will the fuel cost? | Under 1s | yes |
| 10 | hello | Under 1s | yes |

## Screenshots and proof

Uploaded to the pull request on GitHub (dragged into the description):

- three screenshots: question 1's finished answer with its time showing, **More** showing the app
  version, and the phone's memory showing 8 GB;
- a photo of the phone taken with another camera, showing Field Atlas open with the code word from
  the assignment comment typed in the question box, the phone's edge and back in view;
- a screen recording of under a minute showing the phone's model and memory, the app version, and the
  code word typed in the question box.

Phone number, serial number and IMEI are not shown.

## Anything odd

- The app closed once, with no error shown, at the very start when the run was launched, before the
  first question was answered. It did not happen again; every answer below finished and the app
  stayed in front for the rest of the run.
- Questions 5, 7, 8, 9 and 10 were answered by a published emergency guide, map data, the
  calculator and a fixed app reply, so their times (Under 1s to 4s) do not measure the model.
  Questions 1, 2, 3, 4 and 6 do.
- Questions answered by the model took 51s to 3m 15s: 51s (question 3), 1m 31s (question 2), 2m 18s (question 6),
  3m 3s (question 1), 3m 15s (question 4).
- With the model and both collections installed, Field Atlas used 31.73 GB and 30.6 GB were free.
- The phone was on battery during the run, starting at 80%.
- The screenshots were taken after the run. The one for question 2 was taken by reopening that
  same answer from History later on, which is why its status bar shows a different time; the time
  on the answer is the one recorded during the run.
