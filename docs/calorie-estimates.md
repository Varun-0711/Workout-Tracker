# Activity calorie estimates

Workout Tracker stores estimated calories for a completed activity. It uses the activity's MET value, the selected light/moderate/vigorous effort, the most recent body weight saved before the workout began, and the elapsed session time. This follows the app specification's gross MET estimate.

```text
kcal = MET × 3.5 × weight_kg ÷ 200 × minutes
```

The 2024 Adult Compendium defines 1 MET as 3.5 ml oxygen/kg/min and uses about 5 kcal per litre of oxygen for the kcal conversion. The displayed gross estimate includes the resting-energy component during the workout; it is not a lab measurement of an individual. Effort is a practical proxy because the app does not yet capture pace, distance, power, heart rate, or time spent on each strength exercise.

## Activity values

Values are in MET. Where the Compendium has activity-specific values for only one or two intensities, the app uses the nearest listed activity values and may repeat a value across adjacent effort choices. An estimate is for the selected activity category across the session; the app does not claim to measure calorie burn for each individual strength movement because it does not time each movement separately.

| Activity | Light | Moderate | Vigorous | Compendium examples/codes |
| --- | ---: | ---: | ---: | --- |
| Strength | 3.0 | 3.5 | 6.0 | Body-weight resistance 02056; varied resistance 02054; vigorous lifting 02050 |
| Running | 6.5 | 9.0 | 11.8 | 4.0–4.2 mph; 5.5–5.8 mph; 7.5 mph |
| Cycling | 3.5 | 7.0 | 10.0 | Leisure 5.5 mph 01018; moderate 01016; fast 01040 |
| Jogging | 3.3 | 7.5 | 9.0 | Slow jogging; self-selected jogging; vigorous jogging |
| Jump Rope | 8.3 | 11.8 | 12.3 | Slow, moderate, and fast rope skipping 15552/15551/15550 |
| Walking | 2.8 | 3.8 | 4.8 | 2.0–2.4 mph; moderate; brisk |
| Hiking | 3.8 | 5.3 | 6.0 | Slow hillsides; normal pace; cross-country |
| Swimming | 5.8 | 5.8 | 9.8 | Recreational/slow freestyle; fast freestyle |
| Rowing | 5.0 | 7.5 | 11.0 | Stationary rower below 100 W; 100–149 W; 150–199 W |
| Elliptical | 5.0 | 5.0 | 9.0 | Moderate and vigorous effort 02048/02049 |
| Yoga | 2.0 | 2.3 | 4.0 | Nadisodhana; Hatha/general; Power |
| HIIT | 5.0 | 7.0 | 11.0 | Boot-camp proxy; moderate HIIT; vigorous HIIT |
| Dance | 4.8 | 5.5 | 9.8 | Tap; fast ballroom; vigorous nightclub/folk dancing |
| Basketball | 5.0 | 7.5 | 8.0 | Shooting baskets; general; game |
| Soccer | 7.0 | 7.0 | 9.5 | Casual/general 15610; competitive 15605 |
| Tennis | 5.0 | 6.8 | 8.0 | Hitting balls; general; competitive/singles |
| Badminton | 5.5 | 7.0 | 9.0 | Social play; competitive; competitive match |
| Stair Climbing | 4.5 | 6.8 | 9.3 | Slow; general; fast pace |
| Boxing | 5.8 | 7.8 | 12.3 | Punching bag; sparring; in-ring general |
| Volleyball | 3.0 | 4.0 | 6.0 | Non-competitive; general; competitive |
| Pilates | 1.8 | 2.8 | 2.8 | Traditional mat 02103; general 02105 (no separate vigorous value listed) |
| Cricket | 4.8 | 4.8 | 4.8 | Batting, bowling, fielding 15150 (no separate effort values listed) |
| Squash | 7.3 | 7.3 | 12.0 | General 15652; squash 15650 |
| Racquetball | 7.0 | 7.0 | 10.0 | General 15530; competitive 15520 |
| Table Tennis | 4.0 | 4.0 | 4.0 | Table tennis/ping pong 15660 (no separate effort values listed) |
| Martial Arts | 5.3 | 10.3 | 14.3 | Slower novice practice 15425; moderate pace 15430; combat simulation 15432 |
| Tai Chi | 1.5 | 3.3 | 6.0 | Seated/light 15672; general 15670; Yang style 15674 |
| Kickboxing | 5.3 | 7.3 | 10.3 | Nearest martial arts effort examples 15425/15457/15430 |

The activity chooser shows the selected MET value. The history and home screen identify results as estimates. A later version can improve accuracy by recording pace/distance for outdoor cardio, resistance and rest time separately for lifting, and activity-specific options such as swimming stroke or cycling power.

## References

- [2024 Adult Compendium of Physical Activities](https://pacompendium.com/adult-compendium/) and its [activity tables/PDF](https://pacompendium.com/wp-content/uploads/2024/03/1_2024-adult-compendium_1_2024.pdf)
- Compendium tables for [conditioning](https://pacompendium.com/conditioning-exercise/), [running](https://pacompendium.com/running/), [walking](https://pacompendium.com/walking/), [water activities](https://pacompendium.com/water-activities/), [dancing](https://pacompendium.com/dancing/), and [sports](https://pacompendium.com/sports/)
- [Compendium unit conversions](https://pacompendium.com/unite-conversions/)
- [2024 Compendium methods paper](https://pmc.ncbi.nlm.nih.gov/articles/PMC10818145/)
