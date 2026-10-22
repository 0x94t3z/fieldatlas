package xyz.fieldatlas.emergency

import java.util.Locale
import xyz.fieldatlas.research.Evidence

/**
 * Finds the bundled guide for a question that asks what to do about an emergency ("someone is
 * choking", "how to treat a snake bite"). Emergency responses show the saved official guide
 * directly; a generated paraphrase can reverse an instruction despite lexical grounding checks.
 *
 * A topic word alone is not enough ("history of the 2004 tsunami" is research, not first aid);
 * the question must also read as a request for help or safety steps, or be just a few words.
 */
object EmergencyGuideMatch {
    private val intent = Regex(
        "(?i)\\b(how (do|to|can|should)|what (do|to|should|can) (i|we|you)|what to do|treat|treatment|first aid|help|stop|" +
            "prevent|survive|safe|safety|emergency|signs?|symptoms?|someone|somebody|" +
            "my (friends?|child|children|kids?|sons?|daughters?|wife|husband|partner|dad|mom|mother|father|baby|parents)|bitten|stung|burned|burnt|bleeding|" +
            "choking|lost|stranded|injured|hurt|unconscious|not breathing|can'?t breathe|collapsed|prepare|(?:should|do) (?:i|we)|is it safe|purify|disinfect|boil|safest|" +
            "where (?:do|should|can|must) (?:we|i) go|what now)\\b",
    )
    /** Words people use that the sources' keywords miss. */
    private val synonyms = mapOf(
        "cpr" to listOf("cardiopulmonary", "no pulse", "collapsed", "isn't breathing", "is not breathing", "stopped breathing",
            "not breathing", "no breathing", "doesn't breathe", "does not breathe", "won't wake up", "not responding"),
        // How a choking person looks to the people around them: no voice, no cough, hands at the throat.
        "choking" to listOf("choke", "chocking", "something stuck in throat", "can't breathe", "cant breathe", "can't breath", "cant breath",
            "can't talk", "cant talk", "can't speak", "cant speak", "cannot speak", "can't cough", "cant cough", "cannot cough",
            "grabbing his throat", "grabbing her throat", "grabbing their throat", "clutching his throat", "clutching her throat",
            "stuck in his throat", "stuck in her throat", "stuck in my throat", "food stuck", "food is stuck", "piece of food"),
        // Bleeding as it is seen: the amount and the flow, a part cut off, a tourniquet asked for.
        "severe-bleeding" to listOf("bleed", "bleeding badly", "lots of blood", "so much blood", "blood everywhere", "blood spurting",
            "spurting blood", "blood squirting", "squirting blood", "blood is pouring", "blood pouring", "pouring blood", "gushing blood",
            "blood gushing", "blood soaking", "soaking through", "won't stop bleeding", "wont stop bleeding", "tourniquet",
            "sliced off", "chopped off", "severed", "amputated", "fingertip off", "tip of his finger", "tip of her finger", "tip of my finger",
            "deep cut", "deep gash", "gash"),
        "burns" to listOf("burned", "burnt", "scalded", "hot pan", "hot stove", "boiling water", "hot oil", "burned hand", "burned my", "burned her", "burned his",
            "caught fire", "clothes on fire", "red blister", "skin is peeling", "skin peeling", "white and leathery", "touched the oven",
            "touched the stove", "hot iron", "chemical burn", "electrical burn", "burn mark"),
        "fractures-sprains" to listOf("broken leg", "broken arm", "broken ankle", "broken wrist", "sprained",
            "twisted ankle", "twisted my ankle", "rolled my ankle", "swollen ankle", "ankle is swollen",
            "can't put weight", "cant put weight", "cannot put weight", "twisted his ankle", "twisted her ankle",
            "rolled his ankle", "rolled her ankle", "wrist is swollen", "swollen wrist", "swollen and bent", "bent wrist",
            "arm is bent", "leg is bent", "looks broken", "might be broken", "may be broken", "think it's broken", "broken bone",
            // "I think my friend broke her ankle on the trail": the trail words alone pointed to the outdoors guide.
            "broke her ankle", "broke his ankle", "broke my ankle", "broke an ankle", "broke her leg", "broke his leg", "broke my leg",
            "broke her arm", "broke his arm", "broke my arm", "broke her wrist", "broke his wrist", "broke my wrist", "broke a bone",
            // Signs a bystander sees: the shape, the bone, a limb that will not move.
            "bent the wrong way", "bone sticking out", "bone poking out", "sticking out of the skin", "odd angle",
            // Told with a verb: "his bone is sticking out of his leg".
            "bone is sticking out", "bone is poking out", "bone is showing", "bones are sticking out", "bone sticking through",
            "can't move it", "cant move it", "cannot move it", "can't move his arm", "can't move her arm", "can't move his leg", "can't move her leg",
            "swollen and purple", "purple and swollen", "slammed in the car door", "slammed in the door", "rolled my ankel",
            "twisted my knee", "twisted his knee", "twisted her knee", "hurt my ribs", "ribs hurt", "broken rib", "cracked rib", "broken ribs",
            "hip really hurts", "hurt her hip", "hurt his hip"),
        "lost" to listOf("i'm lost", "i am lost", "we're lost", "we are lost", "got lost", "lost in the woods", "lost hiking", "lost on a hike",
            "no idea where i am", "don't know where i am", "dont know where i am", "wrong trail", "lost the trail", "separated from my group",
            "separated from the group", "hasn't come back", "hasnt come back", "has not come back", "didn't come back", "didnt come back"),
        "safe-drinking-water" to listOf("drink", "drinkable", "stream water", "river water", "lake water", "purify water", "disinfect water", "boil water"),
        "signaling" to listOf("signal for help", "rescue signal", "attract rescuers", "call for help without signal"),
        "heat-illness" to listOf("heatstroke", "overheated", "stopped sweating", "not sweating"),
        // The chest as people feel it, and the quieter signs (jaw, breath, sweat) that are easy to call indigestion.
        "heart-attack" to listOf("chest is tight", "tight chest", "chest feels tight", "chest is heavy", "heavy chest", "chest feels heavy",
            "chest hurts", "chest pain", "pain in his chest", "pain in her chest", "pain in my chest", "chest burns", "jaw aching", "aching jaw"),
        "seizures" to listOf("shaking all over", "jerking all over", "jerking", "foaming at the mouth", "having a fit", "fitting"),
        "shock" to listOf("pale and clammy", "cold and clammy", "cold clammy", "clammy skin", "pale sweaty clammy", "in shock"),
        // BE FAST as people describe it: a drooping face, slurred or jumbled words, one weak side, a sudden worst headache.
        "stroke" to listOf("face is drooping", "drooping face", "face droops", "face is droopy", "droopy face", "face droopin",
            "drooping on one side", "droopin on one side", "slurred", "slured", "slurring", "speech is slurred", "words are jumbled", "jumbled words",
            "coming out jumbled", "can't find his words", "can't find her words", "cant find his words", "cant find her words",
            "one side weak", "one side is weak", "weak on one side", "arm keeps drifting", "arm drifting", "half my face", "half his face",
            "half her face", "smile is crooked", "crooked smile", "worst headache", "sudden numbness"),
        "poisoning" to listOf("empty pill bottle", "took too many pills", "took a lot of pills", "swallowed a lot of pills",
            "drank too much", "alcohol poisoning", "drank way too much", "swallowed bleach", "swallowed some bleach", "drank bleach", "swallowed poison", "swallowed pills",
            "swallowed some pills", "swallowed medicine",
            "swallowed detergent", "swallowed antifreeze"),
        "anaphylaxis" to listOf("throat is swelling", "throat swelling", "swollen throat", "tongue is swelling", "lips are swelling"),
        "head-injury" to listOf("hit his head", "hit her head", "hit my head", "hit their head", "banged his head", "banged her head", "bumped his head", "bumped her head",
            "knocked out", "knocked unconscious", "fell on his head", "fell on her head", "landed on his head", "landed on her head",
            "no helmet", "pupils", "one pupil", "same questions over and over", "asking the same questions"),
        // Falling through ice: the water rescue first (from safe ground), then warming the person.
        "drowning" to listOf("fell through the ice", "fallen through the ice", "falls through the ice", "through the ice",
            "was underwater", "under water", "face down in the water", "face down in the pool", "face down in the bath", "face down",
            "can't swim", "cant swim", "fell overboard", "fell off the boat", "car went into the river", "car went into the water",
            "car is sinking", "its sinking", "it's sinking", "swept away", "pulled him out of the water", "pulled her out of the water"),
        "hypothermia" to listOf("fell in a cold", "fell into a cold", "cold river", "cold lake", "freezing water",
            "icy water", "through the ice",
            "body temperature", "shivering", "uncontrollable shivering", "shivering uncontrollably", "stopped shivering",
            "cold to the touch", "cold sea", "capsized", "soaked to the skin", "speech is mumbled", "mumbling"),
        "altitude-sickness" to listOf("mountain sickness", "high altitude"),
        "animal-bites" to listOf("bitten by a dog", "monkey bite", "bat bite", "dog bit", "bit by a dog", "cat bit", "bit by a cat", "monkey bit", "bit by a monkey",
            "monkey scratched", "scratched by a monkey", "dog scratched", "scratched by a dog", "cat scratched", "scratched by a cat",
            "bat scratched", "scratched by a bat", "animal scratch",
            "cat bite", "human bite", "raccoon bit", "fox bit", "bat in the bedroom", "bat in the room", "rabies shot", "rabies shots", "rabis"),
        "earthquake" to listOf("ground is shaking", "ground shaking", "ground started shaking", "building is shaking", "house is shaking"),
        "flood" to listOf("flooded", "flood water", "floodwater", "water is coming up", "water is rising", "water rising"),
        "snakebite" to listOf("bitten by a snake", "snake bit me", "snake bite", "snake bit", "bit by a snake"),
        // "Stung" is not "sting": "my friend was stung by a bee" named only "bee", too weak alone.
        // One plain word, so a swelling throat after a sting still outranks it (anaphylaxis).
        "insect-stings" to listOf("stung"),
        "tick-bites" to listOf("remove a tick", "removing a tick", "tick bit", "bitten by a tick", "bit by a tick",
            // How people find one: "found a tick stuck in my kid's leg".
            "found a tick", "tick stuck", "tick is stuck", "tick embedded", "tick is embedded", "tick attached",
            "remove the tick", "pull the tick", "pull a tick", "get the tick out", "tick's head", "tick head"),
        // The signs, not the word: "two of my fingers are white, hard and numb" after skiing.
        "frostbite" to listOf("frostnip", "white and numb", "hard and numb", "white hard and numb", "numb and white",
            "fingers are white", "toes are white", "fingers are numb", "toes are numb", "numb fingers", "numb toes", "waxy skin",
            "white and waxy", "numb and hard", "numb and grey", "numb and gray", "grey and numb", "frostbitten"),
        "carbon-monoxide" to listOf("gas heater", "space heater", "kerosene heater", "paraffin heater", "generator", "charcoal",
            "engine running", "furnace", "boiler",
            "camp stove", "gas stove", "co alarm", "co detector", "carbon monoxide alarm", "carbon monoxide detector"),
        // An explicit rehydration request or diarrheal symptoms must not become an invented
        // home-mixing recipe. The saved guide describes fluid loss and when to seek care.
        "dehydration" to listOf("diarrhea", "diarrhoea", "rehydration drink", "rehydration solution", "rehydration mixture", "rehydration mix"),
    )

    /**
     * The synonyms above that describe what is happening to a person ("blood is pouring", "can't speak", "bent the
     * wrong way"). Told in these words the signs are a situation in themselves; a thing named ("carbon monoxide
     * detector", "boil water") is not.
     */
    private val signPhrases = setOf(
        "can't talk",
        "cant talk",
        "can't speak",
        "cant speak",
        "cannot speak",
        "can't cough",
        "cant cough",
        "cannot cough",
        "grabbing his throat",
        "grabbing her throat",
        "grabbing their throat",
        "clutching his throat",
        "clutching her throat",
        "stuck in his throat",
        "stuck in her throat",
        "stuck in my throat",
        "food stuck",
        "food is stuck",
        "piece of food",
        "lots of blood",
        "so much blood",
        "blood everywhere",
        "blood spurting",
        "spurting blood",
        "blood squirting",
        "squirting blood",
        "blood is pouring",
        "blood pouring",
        "pouring blood",
        "gushing blood",
        "blood gushing",
        "blood soaking",
        "soaking through",
        "won't stop bleeding",
        "wont stop bleeding",
        "sliced off",
        "chopped off",
        "fingertip off",
        "tip of his finger",
        "tip of her finger",
        "tip of my finger",
        "deep cut",
        "deep gash",
        "caught fire",
        "clothes on fire",
        "red blister",
        "skin is peeling",
        "skin peeling",
        "white and leathery",
        "touched the oven",
        "touched the stove",
        "hot iron",
        "chemical burn",
        "electrical burn",
        "burn mark",
        "bent the wrong way",
        "bone sticking out",
        "bone is sticking out",
        "bone is poking out",
        "bone is showing",
        "bones are sticking out",
        "bone sticking through",
        "bone poking out",
        "sticking out of the skin",
        "odd angle",
        "can't move it",
        "cant move it",
        "cannot move it",
        "can't move his arm",
        "can't move her arm",
        "can't move his leg",
        "can't move her leg",
        "swollen and purple",
        "purple and swollen",
        "slammed in the car door",
        "slammed in the door",
        "rolled my ankel",
        "twisted my knee",
        "twisted his knee",
        "twisted her knee",
        "hurt my ribs",
        "ribs hurt",
        "broken rib",
        "cracked rib",
        "broken ribs",
        "hip really hurts",
        "hurt her hip",
        "hurt his hip",
        "no idea where i am",
        "don't know where i am",
        "dont know where i am",
        "wrong trail",
        "lost the trail",
        "separated from my group",
        "separated from the group",
        "hasn't come back",
        "hasnt come back",
        "has not come back",
        "didn't come back",
        "didnt come back",
        "chest is tight",
        "tight chest",
        "chest feels tight",
        "chest is heavy",
        "heavy chest",
        "chest feels heavy",
        "chest hurts",
        "chest pain",
        "pain in his chest",
        "pain in her chest",
        "pain in my chest",
        "chest burns",
        "jaw aching",
        "aching jaw",
        "shaking all over",
        "jerking all over",
        "foaming at the mouth",
        "having a fit",
        "pale and clammy",
        "cold and clammy",
        "cold clammy",
        "clammy skin",
        "pale sweaty clammy",
        "in shock",
        "face is droopy",
        "droopy face",
        "face droopin",
        "drooping on one side",
        "droopin on one side",
        "speech is slurred",
        "words are jumbled",
        "jumbled words",
        "coming out jumbled",
        "can't find his words",
        "can't find her words",
        "cant find his words",
        "cant find her words",
        "one side weak",
        "one side is weak",
        "weak on one side",
        "arm keeps drifting",
        "arm drifting",
        "half my face",
        "half his face",
        "half her face",
        "smile is crooked",
        "crooked smile",
        "worst headache",
        "sudden numbness",
        "empty pill bottle",
        "took too many pills",
        "took a lot of pills",
        "swallowed a lot of pills",
        "drank too much",
        "alcohol poisoning",
        "drank way too much",
        "knocked out",
        "knocked unconscious",
        "fell on his head",
        "fell on her head",
        "landed on his head",
        "landed on her head",
        "no helmet",
        "one pupil",
        "same questions over and over",
        "asking the same questions",
        "was underwater",
        "under water",
        "face down in the water",
        "face down in the pool",
        "face down in the bath",
        "face down",
        "can't swim",
        "cant swim",
        "fell overboard",
        "fell off the boat",
        "car went into the river",
        "car went into the water",
        "car is sinking",
        "its sinking",
        "it's sinking",
        "swept away",
        "pulled him out of the water",
        "pulled her out of the water",
        "shivering uncontrollably",
        "stopped shivering",
        "cold to the touch",
        "cold sea",
        "soaked to the skin",
        "speech is mumbled",
        "cat bite",
        "human bite",
        "raccoon bit",
        "fox bit",
        "bat in the bedroom",
        "bat in the room",
        "ground is shaking",
        "ground shaking",
        "ground started shaking",
        "building is shaking",
        "house is shaking",
        "water is coming up",
        "water is rising",
        "water rising",
        "white and waxy",
        "numb and hard",
        "numb and grey",
        "numb and gray",
        "grey and numb",
        "engine running"
    ).map(::normalize).toSet()

    /**
     * Guides that a shared symptom must not pick on its own: a headache and dizziness near a gas
     * heater are carbon monoxide, not altitude sickness. The question has to place the person at
     * height for that guide to apply.
     */
    private val requiredContext = mapOf(
        // A person lost outdoors, not a lost passport, phone or wallet.
        "lost" to Regex("(?i)\\b(woods|forest|trail|hik\\w*|trek\\w*|mountains?|desert|wilderness|bush|jungle|outdoors|park|cave|snow|" +
            "dark|night|direction|path|track|way back|route|navigat\\w*|(?:i|we)(?:'m|'re| am| are) lost|got lost|get lost|lost (?:in|on|at|near))\\b"),
        // "How do I clean a gas stove?" is not poisoning: a fuel-burning source needs symptoms or an alarm.
        // Indoors or enclosed use is the hazard itself ("Can I run a generator indoors?").
        "carbon-monoxide" to Regex("(?i)\\b(carbon monoxide|co poisoning|co (?:alarm|detector)|carbon monoxide (?:alarm|detector)|headaches?|dizz\\w*|" +
            "nause\\w*|vomit\\w*|feel(?:s|ing)? sick|faint\\w*|confus\\w*|drowsy|sleepy|unconscious|collapsed|passed out|fumes|" +
            "poison\\w*|indoors|inside|in (?:the|a|my|our) (?:tent|cabin|garage|caravan|camper|room|basement|car))\\b"),
        // Numb white fingers are frostbite only in the cold; elsewhere they can be many things.
        // Everyday "cold" and "ice" do not count: a cold sweat is a heart attack sign, and ice put
        // on a sprain is not weather. "Skin" is not skiing. A freezing temperature counts too
        // ([freezingTemperature]).
        "frostbite" to Regex("(?i)\\b(frost\\w*|cold(?! sweats?\\b| water\\b| drinks?\\b| compress\\w*| packs?\\b| sores?\\b| medicine\\b)|" +
            "freez\\w*|froze\\w*|snow\\w*|ski(?:s|ing|ed|er|ers)?|icy|on (?:the )?ice|through the ice|ice ?(?:storms?|packs?|bags?|cubes?)|cold water for|ice (?:climb\\w*|fish\\w*|axe|field|sheet|skat\\w*|rinks?|hockey)|skating rink|outdoor rink|sled\\w*|sledg\\w*|toboggan\\w*|winter|blizzard|" +
            "below zero|minus|mountains?|hut|glacier|subzero|sub-zero)\\b"),
        // Its steps are for someone hurt far from help; being far from help is not itself an injury.
        "injured-outdoors" to Regex("(?i)\\b(injur\\w*|hurt\\w*|fell|fall(?:s|en)?|broke\\w*|broken|bleed\\w*|sprain\\w*|twist\\w*|cut|cuts|" +
            "wound\\w*|can'?t walk|cannot walk|bitten|stung|burn\\w*|fractur\\w*|dislocat\\w*|unconscious|bruis\\w*|pain\\w*|" +
            // An accident is an injury until shown otherwise: "the car rolled off the road, the driver is trapped and moaning".
            "crash\\w*|accident\\w*|collision|collided|wreck\\w*|trapped|pinned|rolled (?:over|off)|moaning|groaning|crushed)\\b"),
        "altitude-sickness" to Regex("(?i)\\b(altitude|elevation|mountains?|mount|mt|summit|peak|trek(?:king|ked)?|climb(?:s|ed|ing)?|hiked up|high up|" +
            "above sea level|cusco|la paz|lhasa|tibet|quito|bogot[aá]|leh|ladakh|everest|kilimanjaro|himalaya\\w*|andes|" +
            "\\d[\\d,]*\\s*(?:m|meters?|metres?|ft|feet)\\b|ams|hape|hace)\\b"),
    )

    /** Single words specific enough to name the emergency on their own. */
    private val strongWords = setOf(
        "cpr", "choking", "heimlich", "bleeding", "hemorrhage", "haemorrhage", "laceration", "scald", "scalded", "fracture",
        "sprain", "sprained", "dislocation", "concussion", "anaphylaxis", "epinephrine", "epipen", "seizure", "convulsion",
        "poisoning", "overdose", "burned", "burnt", "snakebite", "rattlesnake", "copperhead", "cottonmouth", "antivenom", "scorpion", "hornet",
        "lyme", "rabies", "drowning", "hypothermia", "frostbite", "hyperthermia", "heatstroke", "dehydration", "diarrhea", "diarrhoea", "sunburn",
        "lightning", "earthquake", "quake", "aftershock", "tsunami", "flood", "wildfire", "bushfire", "hurricane", "typhoon",
        "cyclone", "tornado", "blackout", "blizzard", "defibrillator", "resuscitation", "giardia", "mayday", "unresponsive",
        "stranded", "cardiopulmonary", "splint",
        // Weather and water hazards people name directly ("caught in a thunderstorm").
        "thunder", "thunderstorm", "avalanche", "rip current", "flash flood",
    )

    /** Everyday words ("snake", "cut", "lost", "water") count only alongside one of these. */
    private val danger = Regex(
        "(?i)\\b(first aid|treat|treatment|treating|injured|injury|injuries|hurt|hurts|wound|wounded|pain|painful|emergency|" +
            "someone|somebody|bleeding|bled|swollen|swelling|hospital|ambulance|survive|surviving|survival|safety|safe|" +
            "choking|can'?t breathe|cannot breathe|isn'?t breathing|not breathing|no pulse|unresponsive|not responding|collapsed|" +
            "what should i do|what do i do|what to do|help|signs?|symptoms?|bitten|bit me|stung|got (lost|bit|stung|burned|burnt)|" +
            "lost (in|on|while)|stuck|stranded|rescue|unconscious|not breathing|dizzy|faint|fainted|" +
            // How people describe it in the moment: "a snake bit my friend, what do we do?"
            "what (should|do|can|must) (i|we) do|bit by|" +
            "(snake|dog|cat|spider|tick|monkey|bat|animal|something|it) bit (me|him|her|them|us|my|our|his|their)|" +
            "(he|she|they|i|we|and|friend|son|daughter|child|kid|wife|husband|partner|dad|mom|mother|father|baby|brother|sister) " +
            "(is|'s|seems|looks|became|got|getting) confused|shivering|stopped sweating|" +
            "(accidentally|just) swallowed|swallowed (some |a |the )?(bleach|poison|pills?|medicine|tablets?|batter(y|ies)|chemicals?|cleaner|detergent|antifreeze|mushrooms?)|" +
            // "Swallowed a watch battery", "swallowed one of those small round batteries".
            "swallowed (?:(?:a|an|the|one|of|those|these|small|tiny|little|round|flat|watch|button|coin|disc|disk|lithium) )*batter(?:y|ies)|" +
            "hit (his|her|my|their|your|the) head|caught (in|out) (in |by )?(a |the )?(lightning|thunder|storm|thunderstorm|flood|fire|wildfire|blizzard|snowstorm|avalanche|current|rip current)|" +
            // "What's the safest thing to do?" asks for the safety steps.
            "can'?t walk|won'?t stop|safest|what(?:'s| is) the (?:safest|best|right) thing to do)\\b",
    )

    /**
     * A person describing their own situation. A long question qualifies only with one of these:
     * "explain how earthquake warning systems work … public safety" names a hazard and a safety
     * word but asks for research, while a long account of a bite on the trail asks for help.
     */
    private val personalSituation = Regex(
        "(?i)\\b(what (should|do|can|must) (i|we) do|what to do now|" +
            "how (do|should|can) (i|we) (do|perform|give|start|treat|stop|help|survive|get|find|keep|warm|cool|remove|clean|care for)|" +
            // Their own body: "two of my fingers are white, hard and numb".
            "my (fingers?|toes?|hands?|feet|foot|legs?|arms?|skin|face|ears?|nose|eyes?|knees?|ankles?|wrists?|lips?|" +
            "neck|back|head|chest|belly|stomach|shoulders?|hips?|ribs?|throat|mouth|tongue|cheeks?|palms?|thumbs?|elbows?|shins?|calf|calves)|" +
            // A bystander describing a stranger, in the present: "a man has collapsed and is not breathing".
            "(a|an|the|this|some|one) (man|woman|person|child|kid|boy|girl|baby|hiker|driver|player|swimmer|runner|guy|bloke|lad|lady|stranger|" +
            "passenger|teenager|student|customer|cyclist|jogger|worker|tourist)( \\w+){0,4} (has|is|was|just|collapsed|dropped|fell|keeled)|" +
            "(has|have|just) collapsed|(is|isn't|is not|are not|aren't) (not )?(breathing|responding|conscious)|" +
            "(is|are) (unconscious|unresponsive|having a seizure|choking|drowning)|" +
            "(ambulance|help) (is|has been|was|has) (called|coming|on (its|the) way|not arrived)|" +
            "called (911|112|999|000|an ambulance|emergency services)|" +
            "(help|save) (me|us|him|her|them)|i need help|we need help|right now|" +
            "my (friends?|child|children|kids?|sons?|daughters?|wife|husband|partner|dad|mom|mother|father|baby|parents|brothers?|sisters?)|" +
            "(i|we|he|she|they) (have|has) (bad |severe )?diarrh(?:o)?ea|" +
            "(i|we|he|she|they)(?: (?:am|are|is|have been|has been)|['’](?:m|re|s|ve)(?: been)?) (vomiting|throwing up|dehydrated)|" +
            "(i|we|he|she|they) (was|were|got|have been|has been|'ve been|'s been|am|are|is) " +
            "(bitten|stung|burned|burnt|injured|hurt|lost|stranded|stuck|trapped|cut|poisoned|bleeding|choking)|" +
            "someone (is|has|was|got|just))\\b",
    )
    /** Beyond this many words a question must describe a personal situation to be routed to a guide. */
    private const val SHORT_QUESTION_WORDS = 30
    private const val HEAT_BONUS = 3
    /**
     * Outdoors with no building to reach, the backcountry lightning guide is the one that helps;
     * the indoor guide ("go indoors", "do not touch anything plugged in") otherwise outscores it on
     * its "thunder" and "storm" keywords.
     */
    private val backcountry = Regex("(?i)\\b(ridge|summit|trail|hik\\w*|camp\\w*|tent|mountain\\w*|exposed|no shelter|outdoors|backcountry|wilderness)\\b")
    private const val LIGHTNING_BONUS = 3

    /** Someone who can faint or stop responding, unlike the tomato plants and cakes that also "collapse". */
    private const val PERSON = "he|she|they|someone|somebody|anyone|everyone|friend|child|kid|baby|toddler|son|daughter|wife|husband|" +
        "partner|mom|mum|dad|mother|father|grandma|grandpa|grandmother|grandfather|brother|sister|person|man|woman|boy|girl|" +
        "guy|lady|people|patient|hiker|tourist|runner|player|worker"

    /**
     * Heat illness in everyday words: someone "fainted in the heat" or "collapsed in the sun". No
     * single keyword says it, and the model alone once told a bystander to give water to a person
     * who had fainted.
     */
    private val heatCollapse = Regex(
        "(?i)\\b(faint\\w*|collaps\\w*|passed out|pass(?:es|ing)? out|dizz\\w*|confus\\w*)\\b.{0,40}\\b(?:in|from|out in|under) (?:the )?" +
            "(?:heat|hot sun|sun|heatwave|heat wave)\\b|" +
            "\\b(?:in|from|out in|under) (?:the )?(?:heat|hot sun|sun|heatwave|heat wave)\\b.{0,40}\\b(faint\\w*|collaps\\w*|passed out|pass(?:es|ing)? out|dizz\\w*|confus\\w*)",
    )
    /** A person fainted: "how do I stop my tomato plants collapsing in the heat?" is gardening, not first aid. */
    private val someoneFainted = Regex(
        "(?i)\\b(faint\\w*|passed out|pass(?:es|ing)? out|dizz\\w*|confus\\w*|" +
            "(?:i|we|you|$PERSON)(?:['’]s|['’]ve| (?:has|have|had|is|was|were|are|just|nearly|almost|suddenly|then|keeps?|kept))* collaps\\w*|" +
            // "My dad was walking in the sun and collapsed."
            "(?:i|we|you|$PERSON)\\b[^.?!]{0,60}? and (?:then |suddenly )?collapsed)\\b",
    )
    /** In a heat collapse these are the fever, the cooling and the sips of water, not hypothermia, a burn or water safety. */
    private val heatTerms = setOf("body temperature", "cold water", "cool water", "cold", "wet", "water", "drink", "drinking water")

    /**
     * No breathing, no pulse, no response: whatever led to it, CPR comes first. "Not responding to
     * treatment" or "to my texts" is not this sign.
     */
    private val noSignsOfLife = Regex(
        "(?i)\\b((?:not|isn['’]?t|wasn['’]?t|stopped|no longer) breathing|(?:doesn['’]?t|does not) breathe|no pulse|no heartbeat|" +
            "unresponsive|not responding|won['’]?t wake up|(?:$PERSON)\\b(?:[^.?!]{0,60}? and)? (?:isn['’]?t|wasn['’]?t) responding|" +
            // "I put my ear by his mouth and there's nothing", "not answering me": no breath found, no answer given.
            "(?:ear|cheek|hand|face) (?:by|to|over|near|on|next to|against) (?:his|her|their) (?:mouth|nose|chest|lips)[^.?!]{0,30}?" +
            "\\b(?:nothing|no breath\\w*|not (?:a thing|anything))|(?:feel|hear|get) no breath\\w*|no (?:breath|breathing) at all|" +
            "chest (?:isn['’]?t|is not|not) (?:moving|rising)(?: at all)?)\\b(?! to\\b)",
    )
    /**
     * Out but breathing: "breathing but not waking up", "passed out on his back snoring, we can't wake him". The person
     * goes on their side (the recovery position guide), not into CPR, which is for someone who is not breathing.
     */
    private val outSigns = Regex(
        "(?i)\\b(?:won['’]?t|wont|will not|can['’]?t|cant|cannot|couldn['’]?t|not) (?:properly |fully |really )?(?:wake|waking|rouse)|" +
            "\\bnot waking\\b|\\bunconscious\\b|\\bunresponsive\\b|\\b(?:not|isn['’]?t|isnt|is not) responding\\b|\\bout cold\\b|" +
            // "Eyes open but not answering": no answer when spoken to is not responding (set 50).
            "\\b(?:not|isn['’]?t|isnt|won['’]?t|wont) answer(?:ing)?\\b|" +
            // "Won't come round", "won't open her eyes when I say her name" (set 52).
            "\\b(?:won['’]?t|wont|will not|not|isn['’]?t|isnt) (?:come|coming) round\\b|\\b(?:won['’]?t|wont|will not) open (?:his|her|their) eyes\\b",
    )
    private fun outButBreathing(question: String) = outSigns.containsMatchIn(question) && stillBreathing.containsMatchIn(question) &&
        !noBreath.containsMatchIn(question) && !notHappeningNow.containsMatchIn(question)
    private val collapsedNoResponse = Regex("(?i)\\b(?:won['’]?t|wont|doesn['’]?t|does not|isn['’]?t|not) respond(?:ing)?\\b|\\bno response\\b")
    /**
     * A collapse told without "not responding": "coworker just dropped in the warehouse", "she collapsed in the garden".
     * With gasps or no breathing it is an arrest, unless the person is awake ("collapsed, gasping for air, still talking").
     */
    private val collapsed = Regex("(?i)\\b(?:collapsed|keeled over|just dropped|dropped (?:down|to the (?:floor|ground)|while|on the|at the|in the|during|halfway))\\b")
    private val awake = Regex("(?i)\\bgasp\\w* for (?:air|breath)\\b|\\b(?:talking|speaking|says|said|saying|told (?:me|us)|awake|conscious|answering|responding|crying)\\b")
    // A breath only every 6 seconds or more (10 a minute or fewer), the odd breath, or gasping. "Breathing slow" alone is
    // not it: someone passed out drunk and breathing slowly goes on their side.
    private val slowBreathing = Regex(
        "(?i)\\bbreath(?:s|ing|es)?\\b[^.]{0,30}\\bevery (?:[6-9]|[1-9]\\d) seconds\\b|\\bonly (?:a few|the odd) breaths?\\b|" +
            "\\b(?:gasping|agonal) breath|" +
            "\\b[1-8](?: (?:or|to|-) [1-8])? (?:breaths? )?(?:a|per|each) minute\\b|" +
            "\\b(?:[1-4]|one|two|three|four) breaths? (?:while|as|when|in the time|before) (?:i |we )?(?:count(?:ed)?|counting) to (?:20|30|60)\\b|" +
            "\\b(?:[1-4]|one|two|three|four) breaths? in (?:20|30|60) seconds\\b|" +
            "\\b(?:[1-8]|one|two|three|four|five|six|seven|eight) breaths? (?:in|per|a|each) (?:a |one |the )?(?:whole |full |single )?minute\\b|\\blong (?:long )?pauses? between (?:breaths|them|each)\\b",
    )
    private val coldCasualty = Regex("(?i)\\b(?:shiver\\w*|shaking|trembl\\w*|muddled|mumbl\\w*|slurr\\w*|confus\\w*|hypotherm\\w*|frostbit\\w*)\\b")
    private val stillBreathing = Regex("(?i)\\b(?:breathing|breathes|snoring|snores)\\b|\\bchest (?:is )?(?:going|moving|rising) up and down\\b")
    private val noBreath = Regex(
        "(?i)\\b(?:not|isn['’]?t|isnt|wasn['’]?t|stopped|no longer|no|barely|hardly|can['’]?t feel (?:any |his |her |their )?|" +
            "cant feel (?:any |his |her |their )?|can['’]?t see (?:any |him |her |them )?)\\s*(?:breathing|breath)|\\bgasp\\w*|no pulse|no heartbeat|" +
            "\\b(?:heaving|sucking|gulping|snorting|guppy|fish-like) breaths?\\b|like a fish out of water|\\bodd (?:little )?gulps?\\b|" +
            "\\bchest (?:hardly|barely|is hardly|is barely|isn['’]?t really|is not really) (?:mov\\w*|ris\\w*|going up)",
    )
    /** In or out of the water: the drowning guide starts with rescue breaths. */
    private val inWater = Regex(
        "(?i)\\b(drown\\w*|swimm\\w*|underwater|(?:in|into|under|out of) (?:the |a |an |our |my |his |her |their )?(?:\\w+ )?" +
            "(?:water|pool|river|lake|sea|ocean|surf|bath|bathtub|tub|pond|creek|stream|canal|flood|floodwater))\\b",
    )
    /** A blocked or swelling airway: these guides' own steps are for a person who cannot breathe. */
    private val airwayGuides = setOf("choking", "anaphylaxis")

    /** A person in the question: "why is she choking?" describes someone, "why do tsunamis form?" does not. */
    private val someoneInvolved = Regex("(?i)\\b(i|i'm|i've|me|my|we|we're|our|us|you|your|he|he's|she|she's|they|they're|him|her|them|" +
        "his|their|someone|somebody|anyone|friend|child|kid|baby|son|daughter|wife|husband|partner|mom|mum|dad|mother|father|" +
        "brother|sister|person|man|woman|boy|girl|people|patient|hiker|tourist)\\b")

    /**
     * "What causes tsunamis?" and "How do avalanches form?" ask for an explanation, not for first
     * aid; a guide's numbered steps do not answer them. Danger words or a person's own situation
     * still route to the guide ("what is the treatment for a snakebite?"), and "what is the fastest
     * way to stop a seizure?" asks for the steps.
     */
    private val explanation = Regex(
        // "What usually causes seizures?" and "what can cause a seizure?" ask for an explanation as much as "what causes".
        "(?i)^\\s*(?:what (?:(?:usually|typically|commonly|normally|generally|mainly|mostly|actually|really|exactly) )?" +
            "(?:causes|caused|is|are|was|were)\\b(?! the (?:\\w+ ){0,2}(?:ways?|things?) to\\b)|" +
            "what (?:can|could|might|may|would) (?:cause|trigger|lead to)\\b|" +
            "why (?:do|does|did|is|are|was|were)\\b|" +
            "how (?:do|does|did|is|are) (?!i\\b|we\\b|you\\b|my\\b|our\\b|someone\\b|somebody\\b)[\\p{L}' -]{2,40}? " +
            "(?:form|forms|formed|work|works|happen|happens|occur|occurs|start|starts|spread|spreads|develop|develops|differ|differs|get made|made|" +
            "make|makes|keep|keeps|stop|stops|kill|kills|remove|removes)\\b)",
    )

    private val safetyWords = Regex("(?i)\\b(?:safe|safely|safety|safest)\\b")

    /**
     * The guide shown for a matched one: the poisoning match (found by the MedlinePlus summary's words, which the labelled
     * sets were tuned on) shows the NHS page's steps, which say what to do while waiting and that symptoms can come hours later.
     */
    fun shown(guide: EmergencyGuide, guides: List<EmergencyGuide>): EmergencyGuide =
        if (guide.id == "poisoning") guides.firstOrNull { it.id == "poisoning-steps" } ?: guide else guide

    fun match(asked: String, guides: List<EmergencyGuide>): EmergencyGuide? = withoutExpiry(asked).let { question ->
        // Pinpoint pupils, or naloxone already in hand, with slow breathing or no waking: the naloxone guide, which also
        // gives rescue breathing. Set 52's "4 or 5 breaths a minute, pupils tiny" got a memory answer with no call.
        (if (opioidSigns.containsMatchIn(question) && !notHappeningNow.containsMatchIn(question)) guides.firstOrNull { it.id == "opioid-overdose" } else null)
            ?: (if (describesArrest(question)) guides.firstOrNull { it.id == "cpr" } else null)
            ?: rank(question, guides).firstOrNull()?.first
            // Only when nothing else fits: a fever with a fit is the seizure guide, with no wet nappies the dehydration one.
            ?: (if (childFever.containsMatchIn(question) && !onScreen.containsMatchIn(question)) {
                // A fever with a fit is the seizure guide's: "kid was shivering with a fever then started convulsing".
                if (feverFit.containsMatchIn(question)) guides.firstOrNull { it.id == "seizures" } else guides.firstOrNull { it.id == "fever-children" }
            } else null)
            // Something swallowed that no keyword names ("a hearing aid battery", "his tablets twice by mistake"): the
            // poisoning guide, which says to get help even if they seem fine. Set 50 sent these to no guide at all.
            ?: (if (swallowedNow(question)) guides.firstOrNull { it.id == "poisoning" } else null)
    }

    /**
     * "His pen expired last year", "the inhaler ran out in March": a medicine's date, not when the emergency happened. Set 53's
     * wasp sting with a swelling face and a hoarse voice was read as a past event and got no guide and no call line.
     */
    private fun withoutExpiry(question: String): String = expiryDate.replace(question, " ")
    private val expiryDate = Regex("(?i)\\b(?:expired|expires|expiring|went out of date|out of date|ran out|use[- ]by|best before|expiry(?: date)?(?: was| is)?)" +
        "\\s+(?:(?:in|on|at|since|back in|last|this|about|around|a|an)\\s+)?(?:(?:a|an|\\d+|few|couple of)\\s+)?" +
        "(?:january|february|march|april|may|june|july|august|september|october|november|december|(?:19|20)\\d{2}|year|month|week|spring|summer|autumn|fall|winter|" +
        "(?:months?|years?|weeks?) ago)\\b")
    /** Swallowed now and a worry about it: not "ate mushrooms in Italy, which kind were they" or "ate lunch and my battery died". */
    private fun swallowedNow(question: String): Boolean =
        GuideAnswerCheck.swallowedSomething(question) && (swallowConcern.containsMatchIn(question) || harmfulSwallowed.containsMatchIn(question)) &&
            !notHappeningNow.containsMatchIn(question) &&
            !(pastEvent.containsMatchIn(question) && !happeningNow.containsMatchIn(question)) && !onScreen.containsMatchIn(question)
    /** "Drank gasoline", "swallowed two of her pills": the harmful thing right after the verb is the worry itself. */
    private val harmfulSwallowed = Regex("(?i)\\b(?:took|taken|swallowed|swallowing|ate|eaten|drank|drunk|downed|ingested|got into|got hold of|chewed|licked)\\b(?:\\s+[\\w'’-]+){0,3}?\\s+" +
        "(?:pills?|tablets?|capsules?|medicine|medication|meds|bleach|petrol|gasoline|diesel|paraffin|kerosene|antifreeze|batter(?:y|ies)|magnets?|" +
        "chemicals?|detergent|pods?|poison\\w*|cleaner|fluid|white spirit|turps|nicotine|e-?liquid|descaler)\\b")
    private val swallowConcern = Regex("(?i)\\b(?:seems?|fine|ok|okay|sick|ill|queasy|vomit\\w*|throw\\w* up|threw up|puk\\w*|dizzy|drowsy|sleepy|tired|pain|ache|" +
        "flutter\\w*|heart|crying|cries|when (?:he|she|they) swallows?|(?:round|around|on|in) (?:his|her|their) (?:mouth|lips)|(?:lid|cap|top) (?:was )?(?:off|open)|" +
        "should|what (?:do|should)|do (?:i|we)|can (?:i|we|he|she|they)|help|worried|safe|skip|now|just|ago|mistake|accident\\w*|thinking it was|thought it was)\\b")

    private val opioidSigns = Regex("(?i)\\b(?:pinpoint|tiny|very small|pin-?prick) pupils\\b|\\bpupils (?:are |look |gone )?(?:tiny|pinpoint|really small|like pinpricks)\\b|" +
        "^(?=.*\\b(?:naloxone|narcan|nyxoid|prenoxad)\\b)(?=.*\\b(?:gave|given|used|sprayed|injected|second|another|nodd\\w*|breathing|wake|waking|came round)\\b).*")
    private val feverFit = Regex("(?i)\\b(?:convuls\\w*|seizure\\w*|fitting|having a fit|jerking|twitching|eyes rolled)\\b")

    /** A baby or child with a fever ("my baby has a fever of 39°C"): the fever guide, with when to call 111 or 999. */
    private val childFever = Regex("(?i)^(?!.*\\bhay ?fever\\b)(?=.*\\b(?:baby|babies|newborn|infant|toddler|child|son|daughter|kid|\\d+[- ](?:week|month|day)s?[- ]old)\\b" +
        "[^.?!]{0,60}\\b(?:fever|feverish|high temperature|temperature (?:of|is)|temp (?:of|is)|\\d{2}(?:\\.\\d)? ?°? ?c\\b|(?:3[89]|4[01])\\.\\d\\b|feels hot|burning up|hot to (?:the )?touch)).*")

    /**
     * Someone who cannot be woken and is not breathing normally: gasping, or breathing only every few seconds. That is
     * CPR, whatever else the question asks: an overdose "breathing like every 10 seconds" was given the recovery
     * position, and a collapse with "gasping breaths every few seconds" asked about packing for hospital got no guide.
     */
    fun describesArrest(asked: String): Boolean = withoutExpiry(asked).let { question ->
        (outSigns.containsMatchIn(question) || collapsedNoResponse.containsMatchIn(question) ||
            collapsed.containsMatchIn(question) && !awake.containsMatchIn(question)) &&
            (noBreath.containsMatchIn(question) || slowBreathing.containsMatchIn(question)) && !notHappeningNow.containsMatchIn(question)
    }

    /**
     * Whether the question describes someone hurt or unwell now ("my son is wheezing after shrimp"), as opposed to asking
     * about a topic, a past event or a study question: the same test that lets a guide be found by its text.
     */
    /**
     * The guide a learning question is about ("how many compressions a minute for CPR, before my course next week"), by the
     * topic it names. A model that cannot answer from its own knowledge shows this guide as published rather than
     * paraphrasing whatever passage search found: on the 4 GB phone (23 Oct) the 2B answered the CPR question from a
     * research paper with "40 compressions per minute".
     */
    fun learningGuide(question: String, guides: List<EmergencyGuide>): EmergencyGuide? {
        val q = question.lowercase(Locale.ROOT)
        val id = learningTopics.firstOrNull { (regex, _) -> regex.containsMatchIn(q) }?.second ?: return null
        return guides.firstOrNull { it.id == id }
    }

    // Specific topics before the words they contain: sunburn before burns, heat stroke before stroke.
    private val learningTopics = listOf(
        Regex("\\b(?:cpr|chest compressions?|rescue breath\\w*|cardiopulmonary)\\b") to "cpr",
        Regex("\\b(?:choking|heimlich|abdominal thrusts?|back blows)\\b") to "choking",
        Regex("\\b(?:tourniquets?|severe bleeding|stop the bleed\\w*|arterial bleed\\w*)\\b") to "severe-bleeding",
        Regex("\\brecovery position\\b") to "recovery-position",
        Regex("\\b(?:anaphyla\\w+|epipens?|adrenaline (?:auto-?injectors?|pens?))\\b") to "anaphylaxis",
        Regex("\\b(?:naloxone|narcan|opioid overdoses?)\\b") to "opioid-overdose",
        Regex("\\b(?:heat ?stroke|heat exhaustion|heat illness)\\b") to "heat-illness",
        Regex("\\bsunburn\\w*\\b") to "sunburn",
        Regex("\\bburns?\\b") to "burns",
        Regex("\\bheart attacks?\\b") to "heart-attack",
        Regex("\\b(?:strokes?|fast test)\\b") to "stroke",
        Regex("\\bhypothermia\\b") to "hypothermia",
        Regex("\\bfrostbite\\b") to "frostbite",
        Regex("\\bdrown\\w*\\b") to "drowning",
        Regex("\\b(?:seizures?|epilep\\w+|convuls\\w+)\\b") to "seizures",
        Regex("\\b(?:hypoglyc\\w+|low blood sugar|a hypo)\\b") to "low-blood-sugar",
        Regex("\\basthma\\b") to "asthma-attack",
        Regex("\\bsnake ?bites?\\b") to "snakebite",
        Regex("\\bcarbon monoxide\\b") to "carbon-monoxide",
        Regex("\\b(?:fractures?|sprains?|broken (?:bones?|arm|leg|wrist|ankle))\\b") to "fractures-sprains",
        Regex("\\b(?:concussions?|head injur\\w+)\\b") to "head-injury",
        Regex("\\bpoison\\w*\\b") to "poisoning",
        Regex("\\bdehydrat\\w+\\b") to "dehydration",
        Regex("\\baltitude sickness\\b") to "altitude-sickness",
        Regex("\\blightning\\b") to "lightning",
        Regex("\\btick bites?\\b") to "tick-bites",
    )

    fun describesFirstAidSituation(asked: String): Boolean = withoutExpiry(asked).let { question ->
        aPerson.containsMatchIn(question) && bodySign.containsMatchIn(question) && !notFirstAid.containsMatchIn(question) &&
            !notHappeningNow.containsMatchIn(question) && !(pastEvent.containsMatchIn(question) && !happeningNow.containsMatchIn(question))
    }

    /** A guide whose steps would be wrong for this question, whatever its words share with it. */
    private fun ruledOut(guide: EmergencyGuide, question: String): Boolean = when (guide.id) {
        // A place's tap water is that place's travel advice, not water to treat.
        "safe-drinking-water" -> tapWater.containsMatchIn(question) && !waterToTreat.containsMatchIn(question) ||
            waterStorage.containsMatchIn(question) && !treatNow.containsMatchIn(question)
        // "Hiking through long grass, something struck my calf": a hike is not being lost; the guide needs the lost person.
        // Found after being lost, but shivering and confused: the cold, not the getting lost, is the danger now.
        "lost" -> !lostSign.containsMatchIn(question) || coldCasualty.containsMatchIn(question)
        // "A fish bone is stuck, I can breathe and talk but it hurts": the airway is open. The choking steps (back
        // blows, abdominal thrusts) are for someone who cannot breathe, speak or cough.
        "choking" -> !airwayObject.containsMatchIn(question) && otherBreathingCause.containsMatchIn(question) ||
            airwayOpen.containsMatchIn(question)
        // Bleeding in pregnancy or from the vagina is not a wound to press on: the bleeding guides would say so.
        // The severe bleeding guide (tourniquets, packing a wound) is not for a nosebleed or a small cut.
        "severe-bleeding" -> pregnancyBleeding.containsMatchIn(question) || noseBleed.containsMatchIn(question) ||
            minorBleeding.containsMatchIn(question)
        "bleeding-control" -> pregnancyBleeding.containsMatchIn(question) || noseBleed.containsMatchIn(question) ||
            minorBleeding.containsMatchIn(question)
        // A nosebleed with no blow to the head is not a head injury; someone pinned under a shelf has no broken bone to splint.
        "head-injury" -> noseBleed.containsMatchIn(question) && !hitHead.containsMatchIn(question)
        "fractures-sprains" -> crushInjury.containsMatchIn(question) && !brokenBone.containsMatchIn(question)
        // "She gets panic attacks and it's exactly like her usual ones": the person has named it; other chest pain still counts.
        "heart-attack" -> panic.containsMatchIn(question) && usualEpisode.containsMatchIn(question)
        // Food poisoning is an illness, not a swallowed chemical or an overdose: the poison-control guide would mislead.
        "poisoning" -> foodPoisoning.containsMatchIn(question) && !swallowedThing.containsMatchIn(question)
        // A diver's dizziness, tingling and aching after a fast ascent is decompression sickness, not a stroke; the
        // stroke guide's "note the time, call" steps would miss the oxygen and the chamber.
        "stroke" -> diving.containsMatchIn(question)
        "frostbite", "hypothermia" -> headCold.containsMatchIn(question) && !freezingWeather.containsMatchIn(question)
        // Headache, flashing lights and puffy hands at 35 weeks are not bleeding: the bleeding guide was shown for them.
        "pregnancy-bleeding" -> !bleedingWords.containsMatchIn(question)
        // A sting that "burns like crazy" is a sting; a pan, a stove or a bin on fire with nobody burned is a fire to put
        // out, not a burn to cool.
        "burns" -> !heatBurn.containsMatchIn(question) && (marineOrPlantSting.containsMatchIn(question) ||
            stingOrBite.containsMatchIn(question) && !namedBurn.containsMatchIn(question)) ||
            thingOnFire.containsMatchIn(question) && !bodyPart.containsMatchIn(question) && !burnedPerson.containsMatchIn(question)
        else -> false
    }

    private val foodPoisoning = Regex("(?i)\\b(?:food poisoning|stomach bug|tummy bug|gastro\\w*|salmonella|e\\.? ?coli|norovirus|listeria|" +
        "campylobacter|reheated|leftovers?|gone off|out of date|past its date)\\b")
    private val swallowedThing = Regex("(?i)\\b(?:swallow\\w*|overdos\\w*|pills?|tablets?|bleach|chemical\\w*|detergent|antifreeze|" +
        "batter(?:y|ies)|poison(?:ed|ous)|mushrooms?|berries|plant|cleaner|medicine|paracetamol|acetaminophen|ibuprofen)\\b")
    private val diving = Regex("(?i)\\b(?:scuba|div(?:e|es|ed|ing)|diver|ascen(?:t|ded|ding)|came up (?:too )?(?:fast|quick\\w*)|surfaced|" +
        "decompression|the bends|dive computer|depth of \\d+)\\b")
    /** Something burning: "the oil in the frying pan just caught fire", "the toaster is on fire", "bin on fire". */
    private val thingOnFire = Regex("(?i)\\b(?:(?:pan|frying pan|pot|oil|grease|fat|stove|hob|oven|toaster|microwave|kitchen|chip pan|deep fryer|" +
        "candle|curtains?|bin|sofa|couch|mattress|wiring|socket|charger|phone|laptop|car|engine|bbq|barbecue|grill|tent|hood)\\b[^.?!]{0,40}" +
        "\\b(?:on fire|caught fire|catches fire|is burning|are burning|in flames|flames?)\\b|" +
        "\\b(?:fire|flames?)\\b[^.?!]{0,30}\\b(?:pan|frying pan|pot|oil|grease|stove|hob|oven|toaster|kitchen|hood|bin|curtains?|sofa))")
    private val burnedPerson = Regex("(?i)\\b(?:burn(?:ed|t)? (?:my|his|her|their|him|her|me|myself|himself|herself)|got burn(?:ed|t)|" +
        "(?:i|he|she|they|my \\w+) (?:am|is|are|was|were|got) burn(?:ed|t)|blister\\w*|skin)\\b")

    private val noseBleed = Regex("(?i)\\b(?:nose ?bleed\\w*|nose\\b[^.?!]{0,25}?\\b(?:bleed\\w*|blood|pouring|gushing|dripping)|" +
        "(?:bleed\\w*|blood)\\b[^.?!]{0,25}?\\bnose|nostrils?\\b[^.?!]{0,20}?\\bblood)\\b")
    private val hitHead = Regex("(?i)\\b(?:hit|banged|bumped|struck|knocked|smacked|whacked|fell on|landed on|concuss\\w*|skull|punched|headbutt\\w*|fell|fall|crash\\w*|accident)\\b")
    /** Something heavy on a person: no bundled guide covers it. */
    private val crushInjury = Regex("(?i)\\b(?:pinned|trapped under|crushed|crush injur\\w*|(?:fell|landed|collapsed|toppled|came down) on (?:top of )?(?:my|his|her|their|him|me)|" +
        "under (?:a|the) (?:shelving|shelf|shelves|beam|wardrobe|fridge|car|tree|wall|rubble|bookcase|cabinet|unit|tractor|quad|bike|motorbike))\\b")
    private val brokenBone = Regex("(?i)\\b(?:broken|fractur\\w*|bone|bent|deformed|snapped|dislocat\\w*)\\b")
    /**
     * The emergency was then: "had one in March", "a stroke 2 years ago". [happeningNow] keeps a repeat under way
     * ("had a stroke last year and now his face has dropped again").
     */
    private val pastEvent = Regex("(?i)\\b(?:had (?:one|it|an? \\w+(?: \\w+)?) (?:in|back in|last|during|at) \\w+|" +
        "(?:in|back in|since|during) (?:january|february|march|april|june|july|august|september|october|november|december)|" +
        "\\d+ (?:weeks?|months?|years?) ago|(?:weeks|months|years) ago|last (?:week|month|year|summer|winter|spring|autumn|fall)|" +
        "(?:a|one|two|three|\\d+) (?:years?|months?) on|(?:back )?in (?:19|20)\\d{2}|ages ago|a while ago|years back)\\b")
    private val happeningNow = Regex("(?i)\\b(?:just (?:stopped|hit|happened|started|went off|struck|came|collapsed|fell|had|did)|right now|now|currently|" +
        "at the moment|again|keeps?|coming in|rolling in|approaching|getting closer|is (?:here|happening|starting|coming)|" +
        "can see|what do (?:we|i) do|help)\\b")
    private val minorBleeding = Regex("(?i)\\b(?:bleeding (?:a little|a bit|slightly|only a little)|small cut|tiny cut|little cut|paper cut|minor cut|" +
        "(?:bit|bitten) (?:my|his|her|their) (?:tongue|lip|cheek)|graze|scrape)\\b")
    private val headCold = Regex("(?i)\\b(?:(?:a|my|his|her|their|bad|heavy|head|nasty|streaming) cold(?! (?:night|day|morning|evening|water|river|lake|sea|wind|snap|front|spell|room|floor|shower|bath|weather|climate|place|garage|shed|tent|car|one|spot))|" +
        "colds|cold (?:symptoms|sore|and flu|or flu)|runny nose|sneez\\w*)\\b")
    private val freezingWeather = Regex("(?i)\\b(?:freezing|frost\\w*|snow\\w*|ice|icy|below zero|minus \\d|outside (?:all |for )?(?:night|hours)|cold water|lake|river|sea)\\b")
    private val bleedingWords = Regex("(?i)\\b(?:bleed\\w*|blood\\w*|spotting|clots?|miscarr\\w*)\\b")
    private val pregnancyBleeding = Regex("(?i)\\b(pregnan\\w*|weeks? along|miscarr\\w*|vagina\\w*|down there|period|labou?r|contractions?|" +
        "gave birth|after (?:the )?birth|postpartum)\\b")

    // Quoting a reference about safety is different from asking for immediate help.
    // A personal situation always takes priority, including mixed research/help requests.
    private val sourceResearch = Regex(
        "(?i)\\b(?:what (?:do|does|did) (?:the |my |our )?(?:saved |offline )?" +
            "(?:[\\p{L}-]+ ){0,4}(?:guides?|sources?|references?|articles?|papers?) say|" +
            "(?:summarize|summarise|compare|review) (?:the |my |our )?(?:saved |offline )?" +
            "(?:[\\p{L}-]+ ){0,4}(?:guides?|sources?|references?|articles?|papers?))\\b",
    )

    // "Evidence-based first aid for a snakebite", "the proven way to stop bleeding": a request for the steps, worded
    // with care. Only what remains decides whether the question is about evidence.
    private val carefulRequest = Regex("(?i)\\b(?:evidence[- ]based|proven(?=\\s+(?:ways?|methods?|treatments?|steps|techniques?|first aid)\\b))")

    // "What did the PREDIMED trial find?" asks about evidence, not for help, unless a person is in it.
    private val evidenceResearch = Regex("(?i)\\b(?:trials?|stud(?:y|ies)|evidence|proven|research(?:ers)?|meta-analys\\w*|retract\\w*|" +
        "peer[- ]reviewed|statistic\\w*|epidemiolog\\w*)\\b")

    // A place's tap water is its supply, answered by that place's travel guide ("Stay healthy").
    // The treatment guide is for water someone has to make safe, or a supply an event has spoiled.
    private val tapWater = Regex("(?i)\\btap water\\b")
    private val waterStorage = Regex("(?i)\\b(?:stor(?:e|ing|age)|stockpil\\w*|rotate|rotating|rotation|shelf life|how long (?:does|will|can|should) [^.?!]{0,30}?\\b(?:keep|last|stay)|" +
        "keep for|supply for|(?:weeks?|days?|months?) of (?:drinking )?water|gallons|litres? per (?:person|day))\\b")
    private val treatNow = Regex("(?i)\\b(?:purif\\w*|disinfect\\w*|boil\\w*|filter\\w*|tablets?|bleach|iodine|chlorine|contaminat\\w*|dirty|cloudy|murky|brown|" +
        "smell\\w*|safe to drink|can (?:i|we) drink|drinkable|advisory|notice|stream|river|lake|pond|creek)\\b")
    private val lostSign = Regex("(?i)\\b(?:lost|can'?t find|cant find|cannot find|no idea where|don'?t know where|dont know where|which way|wrong (?:turn|trail|path|way)|" +
        "nothing looks familiar|doesn'?t look familiar|separated|hasn'?t come back|hasnt come back|didn'?t come back|didnt come back|not come back|missing|" +
        "stay put|search and rescue|whistle|navigat\\w*|way back|route|path|direction|off (?:the )?trail|off trail|overdue|not (?:back|returned))\\b")
    /** Work talk in emergency words: "inbox is flooded, deadlines hitting like lightning". */
    private val workTalk = Regex("(?i)\\b(?:inbox|deadlines?|e-?mails?|slack|meetings?|workload|spreadsheets?|backlog|prioriti[sz]\\w*|to-?do list|" +
        "kpis?|stakeholders?|sprint|standup|jira|tickets? (?:are|keep)|my boss|clients? (?:are|keep)|quarter(?:ly)?|q[1-4])\\b")
    /** "I am writing an essay comparing how tsunamis are forecast": the hazard is the subject of a piece of work. */
    private val studyTalk = Regex("(?i)\\b(?:essays?|homework|assignments?|dissertation|thesis|school project|class project|quiz|exams?|revision|revising|studying|" +
        "lecture|textbook|paper on|article (?:about|on)|writing (?:about|on|up)|write (?:about|on|up)|researching|research project|presentation (?:on|about))\\b")
    /** Planning for a season or a kit, with nothing happening: the guides as published answer the moment, not the plan. */
    private val planning = Regex("(?i)\\b(?:(?:hurricane|storm|wildfire|fire|tornado|flood|monsoon|cyclone|typhoon|winter|snow|rainy) season|" +
        "how often should (?:i|we) (?:rotate|replace|check|swap)|stockpil\\w*|emergency kit|go bag|bug.?out bag|shelf life)\\b")
    private val waterToTreat = Regex("(?i)\\b(?:purif\\w*|disinfect\\w*|boil\\w*|treat\\w*|filter\\w*|tablets?|bleach|iodine|chlorine|" +
        "stream|river|lake|pond|creek|well|rain\\w*|flood\\w*|storm|hurricane|earthquake|emergency|outage|contaminat\\w*|" +
        "dirty|cloudy|murky|brown|smells?|sick|ill|diarrh\\w*|vomit\\w*|advisory|notice)\\b")

    /**
     * "Stung by a jellyfish… it burns like crazy": the sting's burning pain, not a burn. The burns
     * guide (cool the burn under running water) would send the person to rinse a jellyfish sting
     * with fresh water, which can fire the stingers still on the skin.
     */
    // A sting or bite named as the cause. "It stings" and "a bit" describe a burn's pain and size.
    private val stingOrBite = Regex("(?i)\\b(stung|bitten|bit (?:me|him|her|them|us|my|his|their|our)|" +
        "(?:a|the|my|his|her|their|this|that) (?:sting|bite)|(?:bee|wasp|hornet|insect|scorpion|ant|spider|mosquito|bug|caterpillar|" +
        "horse ?fly|fly|leech|centipede|millipede|tick|flea|midge|sand ?fly|catfish|weever(?: fish)?) (?:stings?|bites?))\\b")
    // A jellyfish or nettle "burned my leg" is still a sting: rinsing it as a burn can make it worse.
    private val marineOrPlantSting = Regex("(?i)\\b(jelly ?fish|(?:portuguese )?man[- ]?o(?:f|['’])?[- ]?war|bluebottle|sea urchins?|stingray|" +
        "lionfish|stonefish|scorpionfish|weever|(?:sea )?anemones?|(?:fire )?coral|(?:stinging )?nettles?)\\b")
    private val heatBurn = Regex("(?i)\\b(fire(?! coral| ants?\\b)|fireworks?|flames?|hot|boiling|scald\\w*|steam|stove|oven|oil|grease|iron|kettle|coffee|tea|" +
        "chemical|acid|electric\\w*|sunburn\\w*|sun|campfire|candles?|exhaust|grill|barbecue|bbq|straightener|curling iron|lighter|" +
        "matches|radiator|pan|pot|hob|heater|toaster|wax|burned (?:by|on|with)|burnt (?:by|on|with))\\b")
    // "Burned my hand" names a burn whatever else the sentence says.
    private val namedBurn = Regex("(?i)\\b(?:burned|burnt|scalded)\\s+(?:(?:my|his|her|their|our|your|the|a)\\s+)?(?:\\w+\\s+)?" +
        "(?:hands?|fingers?|thumbs?|arms?|legs?|palms?|foot|feet|face|skin|wrists?|mouth|tongue|lips?|back|neck|chest|knees?|toes?)\\b|\\bscald\\w*")

    // Frostbite named, or its own signs (white, hard, waxy skin), rather than numbness, which other emergencies share.
    private val frostbiteNamed = Regex("(?i)\\b(frost ?bit\\w*|frost ?nip|frozen (?:fingers?|toes?|feet|hands?|nose|ears?)|waxy|white|hard|blister\\w*)\\b")
    // Acute emergencies that numb fingers can come with; their guide leads and frostbite follows.
    private val outranksNumbness = setOf("cpr", "choking", "severe-bleeding", "shock", "head-injury", "heart-attack", "stroke",
        "anaphylaxis", "seizures", "fractures-sprains", "hypothermia")

    // Something stuck in the airway: the choking guide, even for a person with an allergy.
    private val chokingOnSomething = Regex("(?i)\\b(chok\\w* on|heimlich|stuck in (?:\\w+ )?(?:throat|windpipe|airway)|" +
        "\\w+ (?:is |got )?stuck\\b(?! (?:in|on|at) (?:the |a )?(?:snow|mud|sand|car|traffic|elevator|lift|tent|house|cabin|storm))|no hives)")
    // Hypothermia is the acute danger only when its own signs are there, not just cold water or a frozen lake.
    private val hypothermiaSigns = Regex("(?i)\\b(hypotherm\\w*|shiver\\w*|confus\\w*|drows\\w*|slurred|body temperature|stumbl\\w*|unresponsive)\\b")
    // "Can't breathe" with a tight chest on the stairs or high on a trek is not something stuck in
    // the throat: abdominal thrusts are wrong there. Choking stays when anything points to the airway.
    private val airwayObject = Regex("(?i)\\b(chok\\w*|heimlich|stuck|swallow\\w*|food|eat\\w*|ate|meal|throat|airway|windpipe|" +
        "cough\\w*|gag\\w*|piece|candy|sweet|grape|nut|toy|coin|bone|button|marble)\\b")
    private val airwayOpen = Regex("(?i)\\b(?:can|is able to|are able to|still) (?:still )?(?:breathe|breath|talk|speak)\\b|" +
        "\\b(?:breathing|talking|speaking)(?: and (?:breathing|talking|speaking|swallowing))? (?:fine|ok|okay|normally|normal|alright|all right)\\b")
    private val otherBreathingCause = Regex("(?i)\\b(chest|heart|altitude|elevation|trek\\w*|everest|summit|mountain|asthma\\w*|inhaler|" +
        "smoke|fumes|panic\\w*|stairs|hyperventilat\\w*|stress\\w*|anxious|anxiety|tingling|pinned|trapped|crushed|shelving|shelf|shelves|" +
        "beam|rubble|wardrobe|fell on|on top of)\\b")
    // "Is choking", "swallowed a nut": something in the airway, said outright. It stays anaphylaxis when the
    // person names the allergy, when the trigger is a sting (nothing was swallowed), or with swelling, hives or a
    // rash, which only an allergy shows.
    private val saysChoking = Regex("(?i)\\bchok\\w*|\\bswallow\\w*\\s+(?:a|an|the|some|his|her|their|my|one)\\s+\\p{L}+")
    private val allergyOnlySign = Regex("(?i)\\b(hives|rash\\w*|welts?|itch\\w*|swollen|swelling|puff\\w*|blotch\\w*)\\b")
    // An allergic trigger with airway or swelling signs is anaphylaxis, not a mild sting or choking.
    private val allergyTrigger = Regex("(?i)\\b(stung|stings?|bee|bees|wasps?|hornets?|insect|peanuts?|nuts?|shellfish|allerg\\w*|hay ?fever|smoothie|milkshake)\\b")
    private val allergySign = Regex("(?i)\\b(wheez\\w*|(?:trouble|difficulty|problems?|struggling) (?:to )?breath\\w*|hard to breathe|" +
        "can'?t breathe|cannot breathe|short(?:ness)? of breath|hives|throat (?:is |feels )?(?:tight|closing)|chest (?:is |feels )?tight|feel\\w* faint|" +
        "(?:voice (?:is |has gone |gone |going |getting )?(?:croaky|hoarse)|croaky|hoarse)\\b.{0,60}\\b(?:dizzy|faint|swell\\w*|wheez\\w*|breath\\w*|tight)|" +
        "(?:lips?|tongue|face|throat|mouth|eyes?) (?:is |are |has |have |got |getting )?(?:swollen|swelling|puff\\w*)|" +
        "swollen (?:lips?|tongue|face|throat|mouth))")

    /** A stated temperature at or below freezing: "5°F", "−15 °C", "10 degrees F". */
    internal fun freezingTemperature(question: String): Boolean =
        Regex("(?i)(?<![\\w.])([-−–]?\\d+(?:\\.\\d+)?)\\s*(?:°|º|degrees?)?\\s*(f|c|fahrenheit|celsius)\\b").findAll(question).any { m ->
            val value = m.groupValues[1].replace('−', '-').replace('–', '-').toDoubleOrNull() ?: return@any false
            if (m.groupValues[2].lowercase().startsWith("f")) value <= 32 else value <= 0
        } || Regex("(?i)\\bminus \\d").containsMatchIn(question)

    /**
     * Something wild or unknown was eaten: "we ate some mushrooms we picked … now vomiting". The
     * poisoning guide covers plants and fungi; the danger regex only knew "swallowed".
     */
    private val wildIngestion = Regex("(?i)\\b(?:ate|eaten|eating|eat|swallowed|ingested|chewed|tasted|drank|drunk)\\b[^.?!]{0,40}?\\b" +
        "(?:mushrooms?|toadstools?|fung(?:i|us)|berr(?:y|ies)|plants?|leaves|seeds|roots?|flowers?|bulbs?)\\b")
    private val poisonSign = Regex("(?i)\\b(not sure what|don'?t know what|unknown|poison\\w*|toxic|vomit\\w*|throwing up|cramps?|nause\\w*|" +
        "diarrh\\w*|sick|dizz\\w*|hallucinat\\w*|seizure|unconscious)\\b")

    /**
     * Someone in the situation itself: the asker ("we're in a motel", "our campsite") or a named
     * person. "Them", "people" and a bare "my" are not enough: "why do people get altitude
     * sickness?" and "my iphone lightning cable" describe nobody in danger.
     */
    private val inTheSituation = Regex("(?i)\\b(?:i|i'm|im|i've|ive|we|we're|were|we've|weve|our|he|he's|hes|she|she's|shes|they're|theyre|someone|somebody|" +
        "family|everyone|all of us|the kids|mate|guy|bloke|lad|lady|stranger|passenger|neighbou?r|colleague|coworker|" +
        "my \\d+ ?(?:yr|year|month|mo)s?[- ]old|" +
        "my (?:friends?|child|children|kids?|sons?|daughters?|wife|husband|partner|boyfriend|girlfriend|dad|mom|mum|mother|father|" +
        "baby|parents|brothers?|sisters?|uncle|aunt|grandma|grandpa|group|toddler|little one|teen\\w*|boy|girl|nephew|niece|grandson|" +
        "granddaughter|grandchild|flatmate|roommate|guest|student|patient|client))\\b")

    /**
     * A person and a hazard in the same message, but not in danger now: a story from last year, a film, a song, a school
     * report, or a phone's Lightning cable. "We survived the 2011 earthquake, what magnitude was it" is a lookup.
     */
    private val notHappeningNow = Regex("(?i)\\b(?:yesterday|last (?:night|week|month|year|summer|winter|spring|autumn|fall|time)|" +
        "years? ago|(?:19|20)\\d{2}|(?:movie|film|documentary|song|novel|book|podcast|series|report|essay|article|show)s? (?:about|on|called)|" +
        "(?:band|game) called|lightning (?:cable|charger|port|connector|adapter)|" +
        // "Lived through a typhoon in the 50s", "my band is called Heart Attack": a decade, or a name.
        "in the (?:19|20)?\\d0'?s|(?:is|was|are) (?:called|named))\\b")

    private val bodyPart = Regex("(?i)\\b(hands?|fingers?|thumbs?|palms?|wrists?|arms?|forearms?|elbows?|legs?|knees?|feet|foot|toes?|face|cheeks?|" +
        "lips?|mouth|tongue|eyes?|ears?|neck|chest|back|belly|stomach|skin|blisters?|scald\\w*|bleed\\w*|blood|pain\\w*|hurts?|cut|burned (?:my|his|her)|" +
        "burnt (?:my|his|her)|choking|breath\\w*|unconscious|collaps\\w*|faint\\w*|vomit\\w*|swallow\\w*)\\b")

    /** "Clean the burnt bottom of my pan", "baking times at high altitude", "who invented CPR": cooking, cleaning, trivia. */
    private val kitchenOrHousework = Regex("(?i)\\b(?:bak(?:e|es|ed|ing)|recipes?|cakes?|bread|dough|cocktails?|who (?:invented|discovered|founded|created)|(?:clean|scrub|wash)\\w* (?:the |my |a )?(?:burnt|burned|scorched)|" +
        "(?:burnt|burned|scorched)(?: \\w+){0,4} (?:pans?|pots?|trays?|ovens?|stoves?|hobs?|kettles?|toast|rice|food|bottom|smell)|" +
        "(?:pans?|pots?|trays?|ovens?|stoves?|hobs?)(?: \\w+){0,2} (?:burnt|burned|scorched))\\b")

    /** "When a film shows someone foaming at the mouth": signs on a screen or a page, not in the room. */
    private val onScreen = Regex("(?i)\\b(?:movie|film|documentary|novel|book|series|tv|video|cartoon|game)s? (?:\\w+ ){0,2}" +
        "(?:shows?|showed|showing|where|in which|depicts?)\\b")

    // "Swallowed one of those small round watch batteries", "ate a battery from the remote": only words that describe the
    // battery may sit between, so "ate lunch and my phone battery died" is not a swallowed battery.
    private val batteryIngestion = Regex("(?i)\\b(?:swallowed|swallowing|ingested|ate|eaten)" +
        "(?:\\s+(?:a|an|the|one|two|some|of|those|these|that|this|small|tiny|little|round|flat|shiny|silver|watch|button|coin|" +
        "disc|disk|lithium|hearing[- ]aid|remote|toy|key|fob|cr\\d{4}|lr\\d{2}|ag\\d{1,2}))*\\s+batter(?:y|ies)\\b|" +
        // "The back of the remote is off and the coin battery is gone, I think she swallowed it".
        "\\b(?:coin|button|watch|remote|hearing[- ]aid|lithium|little|small|tiny|flat|round) batter(?:y|ies)\\b[^.?!]{0,60}?" +
        "\\b(?:gone|missing|swallowed|in (?:his|her|their) mouth|can'?t find|cant find)\\b")

    /**
     * Where an object can lodge and be left in for the bleeding guide's padding: the limbs and trunk. The eye and the
     * throat have their own guides, and only a person's own body counts ("his thigh", not "the back room").
     */
    private const val LODGE_PART = "legs?|thighs?|calf|calves|knees?|shins?|feet|foot|toes?|heels?|ankles?|arms?|forearms?|" +
        "hands?|palms?|fingers?|thumbs?|wrists?|elbows?|shoulders?|chest|back|side|stomach|belly|abdomen|gut|tummy|hips?|" +
        "groin|butt|bum|buttocks?|neck|face|cheek|jaw|scalp|head|skin|flesh|body"
    private const val OWNER = "his|her|my|their|your|someone'?s|somebody'?s"
    /** Wood, metal and glass. A tick, splinter, thorn, fish hook or stinger is something else, with other steps. */
    private val lodgedThing = Regex("(?i)\\b(?:branch|branches|stick|sticks|twig|stake|post|pole|rod|bar|rebar|pipe|spike|spikes|" +
        "fence|railing|knife|knives|blade|machete|axe|sword|scissors|screwdriver|chisel|fork|skewer|pencil|pen|arrow|bolt|dart|" +
        "nail|nails|screw|glass|shard|shards|metal|wire|antler|horn|tusk|spear)\\b")
    /** Still in: "stuck in his arm", "is in her calf", "impaled". "Went into my thigh" alone may have come out again. */
    private val lodgedIn = Regex("(?i)\\b(?:impaled|impalement|" +
        "(?:is|was|been|stuck|lodged|embedded|jammed)(?: right| straight| all the way| deep| still)? (?:in|into|in to|inside) " +
        "(?:$OWNER) (?:\\w+ )?(?:$LODGE_PART))\\b")
    private val leftIn = Regex("(?i)\\b(?:still (?:in|inside|stuck|sticking|poking|lodged|embedded)|sticking (?:out|up)|poking out|" +
        "(?:pull|take|yank|get|draw)(?:ing)? (?:it|that|this|the (?:\\w+ )?\\w+) out|remove (?:it|that|this|the (?:\\w+ )?\\w+))\\b")
    private val lodgeBody = Regex("(?i)\\b(?:(?:$OWNER) (?:\\w+ )?(?:$LODGE_PART)|him|her|them|me)\\b")
    // A bone showing is a fracture, and a fish or chicken bone is in the throat.
    private val notLodged = Regex("(?i)\\b(?:ticks?|splinters?|slivers?|thorns?|cactus|spines?|fish ?hooks?|hooks?|stingers?|" +
        "bones? (?:is |are )?(?:sticking|poking|showing)|(?:fish|chicken) bones?|eyes?|eyeballs?|eyelids?|throat|ears?|nose|nostrils?)\\b")

    /**
     * An object lodged in someone's body: "a broken branch went into his thigh, it's still sticking out", "the knife is
     * still in his side, do I pull it out", "impaled on a fence spike". The object is named, and it is stuck in a body
     * part or told as left in someone. A knife that "went into my thigh, blood pouring out" may be out again: that is
     * the severe bleeding guide's. Pain that "went into my left arm" names no object; a car stuck in mud names no body.
     */
    internal fun embeddedObject(question: String): Boolean {
        val text = normalize(question)
        if (notLodged.containsMatchIn(text) || !lodgedThing.containsMatchIn(text)) return false
        return lodgedIn.containsMatchIn(text) || leftIn.containsMatchIn(text) && lodgeBody.containsMatchIn(text)
    }

    /**
     * Every guide the question names, best first with its score, or nothing when the question is
     * not a request for emergency help. [match] is the head; [companions] reads the rest.
     */
    fun rank(question: String, guides: List<EmergencyGuide>): List<Pair<EmergencyGuide, Int>> {
        val text = " " + normalize(question) + " "
        val words = text.trim().split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        if ((sourceResearch.containsMatchIn(question) || evidenceResearch.containsMatchIn(question.replace(carefulRequest, " "))) &&
            !personalSituation.containsMatchIn(question) &&
            !someoneInvolved.containsMatchIn(question)) return emptyList()
        // Someone out but breathing is happening now, however long the message that tells it.
        val sideways = outButBreathing(question)
        // Food poisoning is an illness no guide covers: the poison-control guide would mislead, and the text ranking once
        // handed "throwing up all night" to the head-injury guide. Only a person not breathing changes that.
        if (foodPoisoning.containsMatchIn(question) && !swallowedThing.containsMatchIn(question) && !noSignsOfLife.containsMatchIn(question) && !sideways) return emptyList()
        if (onScreen.containsMatchIn(question) && !personalSituation.containsMatchIn(question)) return emptyList()
        // "Inbox is flooded, deadlines hitting like lightning": work talk, and "water for hurricane season": planning.
        // Nobody is hurt in either, and the guides as published would answer neither.
        if ((workTalk.containsMatchIn(question) || planning.containsMatchIn(question) || studyTalk.containsMatchIn(question)) &&
            !bodySign.containsMatchIn(question) && !noSignsOfLife.containsMatchIn(question)) return emptyList()
        // A burn while cooking still names the body ("burned my hand on the pan"); housework and recipes do not.
        if (kitchenOrHousework.containsMatchIn(question) && !bodyPart.containsMatchIn(question)) return emptyList()
        val dangerous = danger.containsMatchIn(question)
        // A single explanation question about a thing, with no one in it and nothing dangerous happening.
        // "How does a squeeze filter make river water safe? Does it stop viruses?" asks how a thing
        // works: "safe" there describes the water, and a follow-up question about the same thing
        // does not turn it into a request for first aid.
        val dangerBeyondSafety = danger.containsMatchIn(question.replace(safetyWords, " "))
        if (explanation.containsMatchIn(question) && !dangerBeyondSafety && !personalSituation.containsMatchIn(question) &&
            !someoneInvolved.containsMatchIn(question)) return emptyList()
        val heat = heatCollapse.containsMatchIn(question) && someoneFainted.containsMatchIn(question)
        // Guides named by a description of signs in several words ("grabbing his throat", "bone sticking out").
        val describedBySigns = HashSet<String>()
        val scored = guides.map { guide ->
            val terms = (guide.keywords + guide.title + synonyms[guide.id].orEmpty())
                .map(::normalize).filter(String::isNotBlank).distinct()
            // The guide's own subject outranks a related keyword ("earthquake" is also a tsunami keyword).
            val own = setOf(normalize(guide.title), normalize(guide.id.replace('-', ' ')))
            var score = 0
            var specific = false
            for (term in terms) {
                if (" $term " !in text && !(term.length >= 5 && " ${term}s " in text)) continue
                if (heat && term in heatTerms) continue
                val phrase = ' ' in term
                if (phrase || term in strongWords || term in own) specific = true
                if (phrase && term in signPhrases) describedBySigns += guide.id
                score += when {
                    phrase -> term.split(' ').size + 1
                    term in strongWords -> 2
                    dangerous -> 1
                    else -> 0
                } + if (term in own && (' ' in term || term in strongWords || dangerous)) 1 else 0
            }
            if (ruledOut(guide, question)) return@map Triple(guide, 0, false)
            // "Grabbing at his throat", "one side of her face has slid down", "the sea has sucked right back": the sign in
            // the words people use for it, inflected as they say it, names the guide like one of its own phrases.
            if (signsOf[guide.id]?.containsMatchIn(question) == true) {
                score += 4
                specific = true
                describedBySigns += guide.id
            }
            if (guide.id == "poisoning" && (batteryIngestion.containsMatchIn(question) ||
                    wildIngestion.containsMatchIn(question) && poisonSign.containsMatchIn(question))) {
                score += 4
                specific = true
                describedBySigns += guide.id
            }
            // "A branch went into his thigh, it's still sticking out": the object is the emergency, told in no keyword.
            // This guide pads around it and leaves it in; the text ranking once matched the words "branch" and
            // "pull … out" to the drowning guide's rescue with a broad branch.
            if (guide.id == "bleeding-control" && embeddedObject(question)) {
                score += 4
                specific = true
                describedBySigns += guide.id
            }
            // One everyday word is not enough on its own: "fever" is a tick-bite keyword, "throat" a
            // choking one, but "my baby has a fever" and "my throat hurts" are neither.
            // A guide's required context (a height for altitude sickness) is itself specific.
            val context = requiredContext[guide.id]?.let { it.containsMatchIn(question) || guide.id == "frostbite" && freezingTemperature(question) }
            val enough = specific || score >= 2 || context == true
            Triple(guide, if (!enough || context == false) 0 else score, specific)
        }
        // Extra context must not disqualify an explicit request for help, but a long question that only names a hazard is
        // research, not first aid. A long message that describes the signs as they happen is the situation itself.
        // A sting or food with a swelling face or a tight throat is told as it happens too ("husband got stung on the neck by a
        // wasp, face swelling and hes gone hoarse", set 53), with or without "my".
        val reactionTold = allergyTrigger.containsMatchIn(question) && allergySign.containsMatchIn(question)
        if (words.size > SHORT_QUESTION_WORDS && !personalSituation.containsMatchIn(question) && !sideways && !reactionTold &&
            (describedBySigns.isEmpty() || notHappeningNow.containsMatchIn(question)) && !noSignsOfLife.containsMatchIn(question)) return emptyList()
        // The snakebite and flood guides have no CPR step, so a person who is not breathing gets CPR,
        // or the drowning guide's rescue breaths in water. When a choking or swelling airway is the
        // main sign, its own guide stays: those steps are for a person who cannot breathe.
        val top = scored.maxOfOrNull { it.second } ?: 0
        val revive = when {
            !noSignsOfLife.containsMatchIn(question) || sideways -> null
            scored.any { (guide, score, specific) -> guide.id in airwayGuides && specific && score > 0 && score == top } -> null
            inWater.containsMatchIn(question) -> "drowning"
            else -> "cpr"
        }
        // "How long after a heart attack is it ok to fly, my dad had one in March": the emergency was then, and the
        // guide's steps are for now. Only a person not breathing or out cold changes that.
        if (pastEvent.containsMatchIn(question) && !happeningNow.containsMatchIn(question) && revive == null && !sideways) return emptyList()
        // "Fainted in the heat" names no heat-illness keyword; it decides only when no other guide
        // scores at all (hives after peanuts, a spider bite, a stove in the tent), which matters more,
        // and only when a person or a danger sign is in the question.
        // A fuel word alone does not rule it out: pumping gas on a hot day is still the heat.
        val heatOnly = heat && (dangerous || someoneInvolved.containsMatchIn(question)) &&
            scored.none { (guide, score, _) -> guide.id != "heat-illness" && score > 0 }
        // A ridge or a tent in the question: the backcountry lightning guide, when it scores at all.
        val outdoors = backcountry.containsMatchIn(question)
        val numbOnly = !frostbiteNamed.containsMatchIn(question) &&
            scored.any { (guide, score, specific) -> guide.id in outranksNumbness && score > 0 &&
                if (guide.id == "hypothermia") hypothermiaSigns.containsMatchIn(question) else specific }
        val allergic = allergyTrigger.containsMatchIn(question) && allergySign.containsMatchIn(question) &&
            !chokingOnSomething.containsMatchIn(question) &&
            !(saysChoking.containsMatchIn(question) && !allergyOnlySign.containsMatchIn(question) &&
                !Regex("(?i)\\b(allerg\\w*|stung|stings?|bees?|wasps?|hornets?|insect)\\b").containsMatchIn(question))
        val ranked = scored.map { (guide, score, _) ->
            guide to when {
                guide.id == revive -> maxOf(score, 1)
                allergic && guide.id == "anaphylaxis" -> top + 1
                // On their side first: an airway blocked by the tongue or vomit kills faster than most causes of being out.
                sideways && guide.id == "recovery-position" -> top + 1
                numbOnly && guide.id == "frostbite" && score > 0 -> 1
                heatOnly && guide.id == "heat-illness" -> maxOf(score, 1) + HEAT_BONUS
                outdoors && guide.id == "lightning-backcountry" && score > 0 -> score + LIGHTNING_BONUS
                else -> score
            }
        }.filter { it.second > 0 }
        if (ranked.isEmpty()) return byGuideText(question, guides)
        // A stable sort keeps the first of equal guides first, as the bundled order intends.
        val order = ranked.sortedWith(
            compareByDescending<Pair<EmergencyGuide, Int>> { it.first.id == revive }.thenByDescending { it.second }.thenByDescending { if (it.first.urgent) 1 else 0 },
        )
        // "He fell off the ladder and isn't responding" asks for nothing, but the sign is the emergency.
        // People in trouble often write what is happening, not a question: "tornado siren going off
        // and we're in a motel room". A guide named by its own specific term, with a person in the
        // message, is the help; the research and explanation gates above have already run.
        val namedForSomeone = scored.any { (guide, score, specific) -> guide.id == order.first().first.id && specific && score > 0 } &&
            inTheSituation.containsMatchIn(question) && !notHappeningNow.containsMatchIn(question)
        // "Man at the next table is grabbing his throat and can't speak": the signs, told in the words people use for
        // them, are the situation itself, with or without "I" or "my son". The research and explanation gates above
        // have already run, and a story from another time does not count.
        val toldAsSigns = order.first().first.id in describedBySigns && !notHappeningNow.containsMatchIn(question) &&
            !panic.containsMatchIn(question)
        // "I ate shellfish and now I have hives and my throat feels tight": an allergic reaction told as it happens.
        val reacting = allergic && inTheSituation.containsMatchIn(question) && !notHappeningNow.containsMatchIn(question)
        return if (words.size <= 4 || dangerous || revive != null || intent.containsMatchIn(question) ||
            personalSituation.containsMatchIn(question) || namedForSomeone || reacting || toldAsSigns || sideways) order else byGuideText(question, guides)
    }

    /**
     * People describe an emergency in their own words, far more ways than any list of keywords holds: "blood pumping out
     * of his forearm", "clutching his chest, says it feels like someone sitting on him". The medical guides describe the
     * same signs in their own text. When no keyword names a guide, a message that puts a person in the situation and
     * describes a body sign or injury is ranked against the medical guides' full text (BM25), and the best guide is
     * shown only when it clearly leads the next ([TEXT_LEAD]) and scores well on its own ([TEXT_FLOOR]). The research,
     * explanation and on-screen gates have already run, and the per-guide exclusions ([ruledOut]) still apply.
     * Disasters and outdoor hazards are named outright ("flood", "lightning") and stay with the keywords; only the guides
     * for what heat, cold and water loss do to a body join the medical ones.
     */
    private fun byGuideText(question: String, guides: List<EmergencyGuide>): List<Pair<EmergencyGuide, Int>> {
        if (!describesFirstAidSituation(question)) return emptyList()
        // Someone pinned or crushed: no bundled guide covers it, and the ones sharing its words (a blocked airway, a
        // broken bone) would have the helper do the wrong thing.
        if (crushInjury.containsMatchIn(question)) return emptyList()
        // "She gets panic attacks and this is exactly like her usual ones": no bundled guide is for panic, and the text
        // ranking must not overrule the person with a heart attack guide.
        if (panic.containsMatchIn(question)) return emptyList()
        val medical = guides.filter { guide ->
            (guide.category == "medical" || guide.id in bodyConditions) && guide.id !in keywordOnly && guide.id !in notByText && !ruledOut(guide, question) &&
                // A guide's required context still holds: numb fingers at a keyboard are not frostbite.
                requiredContext[guide.id]?.let { it.containsMatchIn(question) || guide.id == "frostbite" && freezingTemperature(question) } != false &&
                bodyConditionContext[guide.id]?.containsMatchIn(question) != false &&
                signContext[guide.id]?.containsMatchIn(question) != false &&
                // A guide for one setting needs that setting: no water, no drowning guide.
                settingContext[guide.id]?.containsMatchIn(question) != false &&
                // A bite or sting guide needs the bite or sting: facial swelling after a pill is not a snakebite.
                biteContext[guide.id]?.containsMatchIn(question) != false &&
                // The allergy guide needs an allergy in the message: a stuck fish bone also makes swallowing hurt.
                (guide.id != "anaphylaxis" || allergyTrigger.containsMatchIn(question) || allergySign.containsMatchIn(question))
        }
        if (medical.size < 2) return emptyList()
        val index = guideIndex(guides)
        val query = textTerms(question).distinct()
        val scored = medical.map { guide -> guide to index.score(guide.id, query) }.sortedByDescending { it.second }
        val (best, top) = scored[0]
        return if (top >= TEXT_FLOOR && top >= TEXT_LEAD * scored[1].second) listOf(best to 1) else emptyList()
    }

    /**
     * Guides only their keywords or their own rule choose, kept out of the text ranking and its word weights: the recovery
     * position's article is about airways and positions, words a cold, smoke or stroke message also uses.
     */
    // The guides added on 22 October (NHS and CDC) match by their keywords only, so the description ranking tuned on the
    // labelled sets keeps its word weights: their text ("chest feeling tight", "sweating") cost a heart attack its lead.
    private val keywordOnly = setOf("recovery-position", "asthma-attack", "low-blood-sugar", "pregnancy-bleeding", "fever-children",
        "knocked-out-tooth", "opioid-overdose", "poisoning-steps")
    /** CPR is for someone not breathing, which its own rule decides; the text ranking never picks it for a reading or an ache. */
    private val notByText = setOf("cpr")
    /** What heat, cold and water loss do to a body, beside the medical guides; weather and terrain guides stay with keywords. */
    private val bodyConditions = setOf("heat-illness", "hypothermia", "frostbite", "dehydration", "sunburn")
    private val usualEpisode = Regex("(?i)\\b(?:usual|always|like (?:her|his|my|their|the) (?:usual|other|last|normal)|same as (?:always|before|usual|last time)|" +
        "exactly like|(?:her|his|my|their) normal|(?:has |this has |it'?s )?happened before|had this before|gets? these|like last time|" +
        "when (?:she'?s|he'?s|i'?m|shes|hes|im|they'?re) (?:stressed|anxious|worried|upset|panick\\w*))\\b")
    private val panic = Regex("(?i)\\b(?:panic\\w*|anxiety attacks?|hyperventilat\\w*|" +
        // Fast breathing with tingling hands or lips, told together, is hyperventilation.
        "breathing (?:really |very |so |too |super )?fast[^.?!]{0,80}?\\btingl\\w*|tingl\\w*[^.?!]{0,80}?\\bbreathing (?:really |very |so |too |super )?fast)\\b")

    /**
     * Signs told as people see them, in whatever inflection ("grabbing at his throat", "the sea has sucked right back",
     * "hair is literally standing up"), scored like one of the guide's own phrases. A guide's required context still
     * applies: a pounding headache is altitude sickness only at altitude.
     */
    private val signsOf = mapOf(
        "choking" to Regex("(?i)\\b(?:(?:grabbing|clutching|clawing|grasping|tugging) at (?:his|her|their|the) (?:throat|neck)|" +
            "hands? (?:at|around|on) (?:his|her|their) (?:throat|neck)|can'?t make (?:a|any) (?:sound|noise)|cannot make (?:a|any) (?:sound|noise)|" +
            "not making (?:a|any) (?:sound|noise)|no sound (?:is )?coming out)\\b"),
        // A diabetic gone sweaty, shaky and confused is low blood sugar, which the stroke guide would miss.
        "stroke" to Regex("(?i)^(?!.*\\b(?:diabet\\w*|insulin|blood sugar|sugar|hypo|glucose)\\b).*" +
            "\\b(?:(?:side of (?:his|her|my|their|the) face|(?:his|her|my|their) face) (?:has |is |was |had |just )?(?:slid|slipped|dropped|fallen|sagged|sagging|slumped)|" +
            "(?:talking|speaking|saying) (?:total |complete |utter |pure |absolute )?gibberish|gibberish|" +
            "(?:vision|eyesight) (?:has |just |suddenly )?(?:went|gone|going|is|turned) (?:double|blurry|blurred)[^.?!]{0,80}\\b(?:can'?t walk (?:straight|properly)|unsteady|dizzy|slurr\\w*|weak|numb|falling over)|" +
            "double vision[^.?!]{0,80}\\b(?:can'?t walk (?:straight|properly)|unsteady|dizzy|slurr\\w*|weak|numb|falling over))\\b"),
        // Grey, clammy skin after a crash or a bleed is shock; with chest, jaw or arm pain and no injury it is the heart.
        "shock" to Regex("(?i)^(?:(?=.*\\b(?:crash\\w*|accident\\w*|collision|wreck\\w*|fell|fall\\w*|came off|knocked|hit by|bleed\\w*|blood|injur\\w*|stab\\w*|" +
            "burn\\w*|bike|motorbike|car|lorry|truck|trampl\\w*)\\b)|(?!.*\\b(?:chest|heart|jaw|arm|arms)\\b)).*" +
            "\\b(?:(?:grey|gray|ashen) and (?:clammy|sweaty)|(?:pale|white) and clammy|clammy and (?:grey|gray|pale|white))\\b"),
        "drowning" to Regex("(?i)\\b(?:(?:dragged|pulled|got|fished|hauled|lifted) (?:him|her|them|me|my \\w+|our \\w+|the \\w+|a \\w+) (?:out|up)(?: of| from)?(?: the| a)? " +
            "(?:pool|water|lake|river|sea|bath|bathtub|pond|canal|surf|waves|ocean|hot tub|jacuzzi)|(?:was|went|been) under(?: the water| water)? for|" +
            "(?:nearly|almost) drowned|(?:inhaled|breathed in) (?:a lot of |some |loads of |lots of )?(?:pool |sea |bath )?water)\\b"),
        "earthquake" to Regex("(?i)\\b(?:everything(?:'s| is)? shaking|whole (?:building|house|room|flat|apartment|place) (?:is )?(?:shaking|swaying|moving|rocking)|" +
            "shelves (?:are )?(?:falling|coming down)|(?:walls|floor|building|ground) (?:is |are )?(?:swaying|shaking|moving|rolling)|" +
            "things (?:are )?falling off (?:the )?(?:shelves|walls)|(?:light|lamp|chandelier) (?:is )?swinging)\\b"),
        "seizures" to Regex("(?i)^(?!.*\\b(?:pods?|detergent|soap|dishwasher|washing|bleach|chemical|shampoo|bubble|toothpaste|capsules?)\\b).*" +
            "\\b(?:whole body (?:is )?(?:jerking|shaking|convulsing|twitching|stiff)|eyes (?:have )?rolled back|eyes rolling back|" +
            "had a (?:fit|seizure)[^.?!]{0,80}?\\b(?:groggy|confused|sleepy|drowsy|out of it|doesn'?t know|dont know|bitten (?:his|her) tongue|wet (?:himself|herself)|lie (?:him|her) down)|" +
            "(?:foam|foaming|froth|frothing)(?: coming)? (?:at|around|from|out of) (?:his|her|the|their) mouth|jerking (?:all over|uncontrollably|violently)|" +
            "(?:arms|legs|limbs) (?:are )?(?:jerking|thrashing|flailing)|went (?:stiff|rigid) and (?:started )?(?:shaking|jerking)|" +
            "bit(?:ten)? (?:his|her|their|my) tongue[^.?!]{0,80}\\b(?:wet|wee|peed|confus\\w*|floor|groggy)|(?:wet (?:himself|herself|themselves)|pyjamas are wet)[^.?!]{0,80}\\bbit(?:ten)? (?:his|her|their) tongue)\\b"),
        "lost" to Regex("(?i)\\b(?:nothing looks familiar|doesn'?t look familiar|(?:don'?t|dont|not sure|no idea) (?:know )?(?:which|where|what) (?:way|direction|path|trail)|" +
            "(?:can'?t|cant|cannot) find (?:the|our|my) (?:way|trail|path|car|camp|tent|way back)|off (?:the )?trail|wrong (?:turn|trail|path)|" +
            "keep walking or stay put|stay put or|been walking (?:for )?(?:hours|ages)|going in circles|same (?:rock|tree|spot) (?:again|twice))\\b"),
        "fractures-sprains" to Regex("(?i)^(?=.*\\b(?:ankle|wrist|arm|leg|finger|thumb|knee|elbow|foot|toe|shoulder|collarbone|hip|shin|forearm)\\w*\\b)" +
            "(?!.*\\b(?:bit|bite|bitten|sting\\w*|stung|snake|spider|back went out|slipped disc|sciatica)\\b).*" +
            "\\b(?:bent (?:sideways|backwards|the wrong way|at a (?:\\w+ )?angle|outwards|inwards|funny|weird)|pointing the wrong way|sticking out (?:sideways|at an angle)|" +
            "(?:can'?t|cant|cannot) (?:stand on (?:it|the|my|his|her|that)|put (?:any )?weight|bear weight|walk on it|move it|straighten it)|" +
            "heard (?:a|it) (?:crack|snap|pop)|felt (?:a|it) (?:crack|snap|pop))\\b"),
        "wildfire" to Regex("(?i)\\b(?:sky (?:is |has gone |has turned |went |turned |looks )?(?:orange|red|dark orange|blood red)|ash (?:is )?(?:falling|landing|raining|coming down)|" +
            "flames? (?:on|over|coming over|along|up|down) the (?:ridge|hill|hillside|mountain|valley|slope|next street|road)|" +
            "smoke (?:is )?(?:getting|coming) (?:into|in|thicker|closer|worse)|embers? (?:are )?(?:landing|falling|blowing)|evacuat\\w* (?:order|warning|alert|zone)|" +
            "(?:fire|flames?) (?:is |are )?(?:coming|getting|moving|spreading) (?:closer|towards us|this way|our way|fast)|bush ?fire|forest fire)\\b"),
        "snakebite" to Regex("(?i)^(?=.*\\b(?:grass|bush|scrub|undergrowth|rocks?|logs?|trail|hik\\w*|desert|creek|river ?bank|woodpile|garden|field|paddock|outback|jungle|forest|woods|camp\\w*)\\b).*" +
            "\\b(?:(?:two|2) (?:small |tiny |little )?(?:puncture|fang|bite) (?:holes|marks|wounds|punctures)|puncture (?:marks|holes|wounds)|fang marks|" +
            "something (?:struck|bit|got|nipped) (?:my|his|her|their) (?:calf|ankle|leg|foot|hand|arm|finger|toe|shin|heel|wrist))\\b"),
        "head-injury" to Regex("(?i)\\b(?:(?:head|skull) (?:hit|smacked|slammed|struck|smashed|cracked|bounced) (?:the|a|on|into|against|off)|" +
            "(?:hit|smacked|banged|cracked|whacked|bashed|split) (?:the back of |the side of |the front of )?(?:his|her|my|their) (?:head|skull)|" +
            "came off (?:his|her|my|their) (?:skateboard|bike|scooter|board|horse|motorbike)[^.?!]*\\bno helmet|no helmet[^.?!]*\\b(?:head|kerb|curb|concrete|pavement|ground))\\b|" +
            "^(?=.*\\b(?:head|skull|helmet|kerb|curb|fell|fall\\w*|hit|banged|knocked|crash\\w*|came off|concuss\\w*|bike|skateboard|ladder|stairs|radiator|tackle\\w*)\\b).*" +
            "\\b(?:same (?:thing|question)s? (?:over and over|again and again|repeatedly)|(?:keeps|kept) asking (?:me |us )?(?:the same|what happened|where (?:he|she) is)|" +
            "doesn'?t (?:know|remember) (?:where (?:he|she) is|what happened|what day))\\b"),
        "altitude-sickness" to Regex("(?i)\\b(?:(?:pounding|splitting|crushing|terrible|awful|worst|banging|thumping) headache|stumbling|staggering|" +
            "(?:like|as if) (?:he'?s|she'?s|they'?re|i'?m|hes|shes) drunk|can'?t walk (?:straight|in a straight line))\\b"),
        "tornado" to Regex("(?i)\\b(?:(?:see|seeing|spotted|spot|there'?s|theres|watching) (?:a |the |this )?(?:huge |big |massive |dark )?funnel|" +
            "funnel (?:coming|touching|touched|on the ground|out (?:of )?the window|in the distance|dropping|forming)|" +
            "sky (?:is |has gone |has turned |went |turned |looks )?(?:a )?(?:weird |dark |sickly )?green\\w*|(?:tornado|storm|warning) sirens?|" +
            "sirens? (?:are |is )?(?:going off|wailing)|tornado (?:warning|watch|alert))\\b"),
        "tsunami" to Regex("(?i)\\b(?:(?:sea|water|ocean|tide|waves?) (?:has |have |is |are |was |just |suddenly )?(?:sucked|drawn|drew|pulled|receded|receding|drained|rushed|disappeared)" +
            "(?: right| all| way| suddenly| really| completely)? (?:back|out|away)|(?:sea|water|ocean|tide) (?:has |is |just )?(?:gone|went|disappeared)(?: right| way| really)? out|" +
            "wall of water|tsunami (?:siren|warning|alert))\\b"),
    ) + listOf("lightning", "lightning-backcountry").associateWith {
        // Hair on end before a strike, with the weather or the ground that makes it lightning and not a chill.
        Regex("(?i)^(?=.*\\b(?:storm\\w*|thunder\\w*|lightning|clouds?|ridge|summit|peak|hik\\w*|camp\\w*|outside|outdoors|field|golf|beach|treeline|tree line)\\b).*" +
            "\\b(?:hair (?:on (?:my|his|her|our|the back of my|the back of his|the back of her) (?:arms?|neck|head|legs?) )?(?:is |was |has |are |just )?" +
            "(?:literally |all |just |suddenly |actually )?(?:standing|stands|stood|sticking|stuck) (?:up|on end|straight up)|" +
            "(?:poles?|rope|ice axe|tent poles?|metal|fence|zip\\w*) (?:are |is )?(?:buzzing|humming|crackling)|skin (?:is )?(?:tingling|buzzing|crackling))\\b")
    } + mapOf(
        // A child who ate something from the house that is poison in a mouthful: a cigarette, a vape pod, a detergent pod.
        "poisoning" to Regex("(?i)\\b(?:ate|eaten|eating|swallowed|swallowing|chewed|chewing|chewed on|chewing on|bit into|bitten into|bit|biting|drank|drinking|" +
            "licked|licking|sucked on|sucking on|got into|got hold of)" +
            "(?:\\s+(?:a|an|the|some|one|two|half|half a|my|his|her|mum'?s|dad'?s|of|out of|from|bit of|piece of|whole|couple of|few|lot of|load of|" +
            "those|these|that|this|them|it|another|more|lots of|loads of|something|one of|handful of))*\\s+" +
            "(?:cigarette\\w*|cig|cigs|butt|butts|ashtray|tobacco|nicotine|vape\\w*|e-?cig\\w*|e-?liquid|juul|pods?|detergent|" +
            "dishwasher (?:pods?|tablets?|tabs?)|(?:washing|laundry|detergent|washing machine) (?:pods?|tablets?|tabs?|capsules?|liquitabs?|liquid|gel)|liquitabs?|" +
            "mouthwash|hand sanitis\\w*|hand sanitiz\\w*|sanitiser|sanitizer|essential oils?|nail polish remover|acetone|antifreeze|" +
            "weed ?killer|rat poison|slug pellets|mothballs?|lamp oil|lighter fluid|paint thinner|white spirit|turps|perfume|aftershave|" +
            "(?:iron|vitamin|sleeping|blood pressure|diabetes|heart|pain) (?:pills?|tablets?)|gummies|edibles?|cannabis|weed|magic mushrooms?)\\b"),
        // The signs in the NHS guides' own call lists, told the way set 50 told them. The guides' single keywords
        // ("asthma", "insulin") score nothing alone, so these situations reached no guide.
        // Their reliever named, with a breathing sign: wheezing, too breathless to finish a sentence, the inhaler not helping.
        "asthma-attack" to Regex("(?i)^(?=.*\\b(?:asthma\\w*|inhalers?|puffers?|reliever|ventolin|salbutamol|albuterol|seretide|symbicort|preventer|blue one)\\b)" +
            "(?=.*\\b(?:wheez\\w*|breathless|out of breath|(?:can'?t|cant|cannot|struggling to|trouble) (?:breathe|breathing|catch)|cough\\w*|tight chest|chest (?:is |feels )?tight|" +
            "sentence|neck muscles|(?:not|isn'?t|isnt|barely|hardly) (?:helping|working|doing)|doing nothing|no (?:better|difference|change)|tight[- ]chested|still tight|gasp\\w*|words at a time|(?:can|could) only (?:say|get out|manage))\\b).*"),
        // A hypo by name or by its signs in someone on insulin; a meter reading HI, ketones or pear-drop breath are high sugar.
        "low-blood-sugar" to Regex("(?i)^(?!.*(?:\\b(?:says|reads|shows|showing|reading) hi\\b|\\bhigh\\b|\\bketone\\w*|pear drops|nail polish)).*" +
            "(?:\\b(?:hypo|hypos|hypoing|having a hypo|going low|having a low|low blood sugar|low sugar)\\b|" +
            "\\b(?:blood )?sugar(?:s)? (?:is |are |has |'s |went |gone |going |keeps )?(?:low|dropp\\w*|crash\\w*|plummet\\w*)\\b|" +
            "^(?=.*\\b(?:diabet\\w*|insulin|type 1|type one)\\b).*\\b(?:shak\\w*|trembl\\w*|sweat\\w*|clammy|confus\\w*|not making sense|drowsy)\\b|" +
            // A reading under 4 mmol/L ("his meter said 3.1 ... now it says 3.4"): set 52's reversed hypo treatment, every model.
            "\\b(?:meter|reading|sugar|levels?|bm|glucose|monitor|libre|sensor)\\b[^.?!]{0,25}\\b(?:said|says|reads|read|showed|shows|is|was|at|of|dropped to|down to)\\s*[0-3]\\.\\d\\b)"),
        // Arm or jaw pain with sweating or sickness, called indigestion or not; after a fall or a bite it is the injury.
        "heart-attack" to Regex("(?i)^(?=.*\\b(?:sweat\\w*|clammy|sick|nause\\w*|indigestion|heartburn|breathless|short of breath|grey|gray|pale)\\b)" +
            "(?!.*\\b(?:fell|fall|fallen|hit|banged|crash\\w*|twist\\w*|broke\\w*|bone|sprain\\w*|bit|bite|bitten|stung|sting\\w*)\\b).*" +
            "\\b(?:(?:left |right )?arm (?:aches|aching|hurts|is (?:aching|sore|hurting|numb|heavy))|(?:ache|aching|pain) (?:in|down) (?:his|her|my|their|the) (?:left )?arm|" +
            "jaw (?:aches|aching|hurts|pain)|pain (?:going|spreading|moving|radiating) (?:in(?:to)?|down|up|to) (?:his|her|my|their|the) (?:left )?(?:arm|jaw|neck|back))\\b|" +
            // "Heartburn" that came with effort and now will not go at rest, in an older person (set 52): angina turning into a heart attack.
            "^(?=.*\\b(?:heartburn|indigestion|chest (?:pain|pressure|tightness|is tight|feels tight|heavy))\\b)(?=.*\\b(?:[5-9]\\d|walk\\w*|hill|stairs|exert\\w*|exercis\\w*)\\b)" +
            "(?=.*\\b(?:won'?t go(?: away)?|wont go(?: away)?|not going away|isn'?t going|at rest|resting|sitting (?:down|still|watching)|watching tv)\\b).*"),
        "opioid-overdose" to opioidSigns,
        // A chemical on the skin is a burn to rinse: drain unblocker, oven cleaner, bleach, wet cement.
        "burns" to Regex("(?i)\\b(?:spill\\w*|splash\\w*|spilt|got|poured|knocked|dripped)\\b[^.?!]{0,30}\\b(?:drain (?:cleaner|unblocker)|oven cleaner|bleach|caustic|lye|acid|" +
            "wet cement|cement|concrete|chemical\\w*|ammonia|chlorine|pool (?:chemicals?|shock|granules)|descaler|caustic soda)\\b[^.?!]{0,30}\\b(?:on|onto|over|down)\\b[^.?!]{0,20}\\b(?:skin|hands?|arms?|forearms?|legs?|face|neck|feet|foot|fingers?|chest|wrists?)\\b"),
        // A night outside and now shaking or muddled: the cold is the danger ("found in the park this morning in her nightie").
        // A wound or a crash in the rain is the injury first: the bleeding guide stays.
        "hypothermia" to Regex("(?i)^(?!.*\\b(?:bleed\\w*|blood|crash\\w*|broken|bent|fractur\\w*|wound\\w*|cut)\\b)(?=.*\\b(?:overnight|all night|night (?:outside|out(?:doors)?|in the)|outside all night|in the (?:cold|snow|rain)|freezing|soaked|wet through)\\b).*" +
            "\\b(?:shiver\\w*|shaking|trembl\\w*|muddled|confus\\w*|drowsy|slurr\\w*|mumbl\\w*|cold to (?:the )?touch|ice cold)\\b"),
    )
    private val biteContext = mapOf(
        "snakebite" to Regex("(?i)\\b(snakes?|adder|viper|cobra|rattle\\w*|copperhead|cottonmouth|mamba|krait)\\b"),
        "spider-bites" to Regex("(?i)\\b(spiders?|black widow|recluse|redback|funnel[- ]web)\\b"),
        "tick-bites" to Regex("(?i)\\b(ticks?|lyme)\\b"),
        "animal-bites" to Regex("(?i)\\b(bit|bite|bitten|scratch\\w*|rabi\\w*)\\b"),
        "insect-stings" to Regex("(?i)\\b(sting\\w*|stung|bees?|wasps?|hornets?|yellow ?jackets?|scorpions?|fire ants?|ants?)\\b"),
        // Something in the airway, not a bug latched on the skin.
        "choking" to Regex("(?i)\\b(chok\\w*|throat|swallow\\w*|food|eat\\w*|ate|piece|bite of|mouthful|went down wrong|wrong pipe|windpipe|airway|" +
            "coin|marble|grape|nut|candy|sweet|toy|bone|button|bead|balloon|can'?t (?:breathe|breath|cough|talk|speak))\\b"),
        // A burn's cause, not a fever that feels "burning hot".
        "burns" to Regex("(?i)\\b(blister\\w*|scald\\w*|caught (?:fire|on)|on fire|flames?|singed|charred|boiling|steam|oil|grease|stove|oven|iron|" +
            "kettle|candles?|grill|bbq|barbecue|chemical|acid|electric\\w*|welde?r?\\w*|sparks?|fireworks?|lighter|exhaust|radiator|" +
            "burn(?:ed|t)? (?:my|his|her|their|on|from|by|with)|burn (?:mark|on)|burnt|burned)\\b"),
    )
    /** What each of them needs in the message besides a body sign: the sun, heat, cold or wet, fluid lost. */
    /** A guide the text ranking may pick only when its own signs are in the message, not a shared word like "sudden". */
    private val signContext = mapOf(
        "stroke" to Regex("(?i)\\b(?:face|facial|speech|speak\\w*|slur\\w*|words?|arms?|weak\\w*|numb\\w*|droop\\w*|lopsided|confus\\w*|" +
            "headache|vision|balance|dizz\\w*|one side|half (?:his|her|my|the) (?:face|body))\\b"),
    )
    private val bodyConditionContext = mapOf(
        "sunburn" to Regex("(?i)\\b(sun\\w*|beach|uv|tann\\w*|outside all day|pool|lake|sea|ski\\w*)\\b"),
        "heat-illness" to Regex("(?i)\\b(heat\\w*|hot|sun\\w*|summer|desert|temperature|degrees?|°|humid\\w*|sauna|marathon|march\\w*)\\b"),
        "hypothermia" to Regex("(?i)\\b(cold\\w*|freez\\w*|froze\\w*|icy|ice|snow\\w*|wet|soaked|rain\\w*|water|winter|wind\\w*|shiver\\w*)\\b"),
        "dehydration" to Regex("(?i)\\b(drink\\w*|drank|fluids?|water|diarrh\\w*|vomit\\w*|throwing up|pee\\w*|urin\\w*|wee|thirst\\w*|dry mouth|nappies|diapers?)\\b"),
    )

    /**
     * Guides for one setting, which the text ranking may pick only when the message is in it. Shared words are not the
     * setting: the drowning guide's rescue "with a broad branch" once answered a bike crash with a branch in the thigh.
     */
    private val settingContext = mapOf(
        "drowning" to Regex("(?i)\\b(drown\\w*|water\\w*|swim\\w*|underwater|pool|river|lake|sea|ocean|surf\\w*|beach|bath\\w*|tub|" +
            "pond|creek|stream|canal|flood\\w*|boat\\w*|kayak\\w*|canoe\\w*|waves?|ice|current|overboard|submerged)\\b"),
        "eye-injury" to Regex("(?i)\\b(eyes?|eyeballs?|eyelids?|vision|sight|blind\\w*)\\b"),
        "carbon-monoxide" to Regex("(?i)\\b(carbon monoxide|co (?:alarm|detector|poisoning)|fumes?|gas|heater|generator|stove|furnace|boiler|" +
            "charcoal|grill|bbq|barbecue|exhaust|engine|fireplace|wood ?burner|garage|tent|smoke)\\b"),
    )

    private const val TEXT_FLOOR = 8.0
    private const val TEXT_LEAD = 1.5

    /** Someone in the message: the writer, a relative, a bystander. */
    private val aPerson = Regex("(?i)\\b(i|i'm|im|we|we're|my|our|he|she|his|her|hes|shes|him|someone|somebody|toddler|baby|kid|kids|child|son|" +
        "daughter|man|woman|guy|lady|boy|girl|friend|mate|wife|husband|mum|mom|dad|grandma|grandpa|uncle|aunt|sister|brother|teenager|student|" +
        "coworker|neighbou?r|mother|father|partner|player|hiker|driver|passenger|patient|stranger)\\b")
    /** A sign on the body or an injury, as people describe them. */
    // "Pain", "painful", "painkillers", not "painted" (set 51: "who painted girl with a pearl earring" got the first-aid reply).
    private val bodySign = Regex("(?i)\\b(pain(?:s|ful\\w*|killers?)?|hurt\\w*|ache\\w*|aching|bleed\\w*|blood\\w*|swell\\w*|swollen|puffy|breath\\w*|cough\\w*|chok\\w*|" +
        "vomit\\w*|puk\\w*|threw up|throwing up|dizz\\w*|faint\\w*|collaps\\w*|unconscious|passed out|wont wake|won't wake|can'?t wake|drows\\w*|" +
        "confus\\w*|slurr\\w*|numb\\w*|tingl\\w*|burn\\w*|blister\\w*|scald\\w*|bit|bite|bitten|sting\\w*|stung|broke\\w*|broken|fractur\\w*|" +
        "sprain\\w*|twist\\w*|bent|lump|bump|bruis\\w*|fell|fall|fallen|crash\\w*|hit|cut|gash|wound\\w*|stab\\w*|seiz\\w*|fit|shak\\w*|" +
        "twitch\\w*|rigid|stiff|jerk\\w*|fever\\w*|chills|sweat\\w*|clammy|pale|grey|gray|blue|purple|white|waxy|rash|hives|itch\\w*|poison\\w*|" +
        "swallow\\w*|overdos\\w*|pills?|pulse|heart|chest|can'?t (?:move|feel|see|talk|speak|walk|stand|lift)|" +
        "cannot (?:move|feel|see|talk|speak|walk|stand)|not moving|not breathing|limp|quiet)\\b")
    /** Research, trivia, shopping and planning: words that share a guide's vocabulary without anyone in danger. */
    private val notFirstAid = Regex("(?i)\\b(who invented|history of|why do|why does|how does|what is a|what are|best brand|which brand|buy|kit|" +
        "what'?s (?:a |the )?normal|what is (?:a |the )?normal|normal range|is (?:that|this|it|mine) (?:high|low|normal|ok|okay|fine|too high|too low)|" +
        "differences? between|studying|exams?|homework|quiz|essay|assignment|lecture|textbook|revision|revising|" +
        "movie|film|book|recommend|price|cost|statistics|study|studies|research|invented|famous|documentary|trivia)\\b")

    private class GuideIndex(val docs: Map<String, Map<String, Int>>, val lengths: Map<String, Int>, val df: Map<String, Int>, val avg: Double) {
        fun score(id: String, query: List<String>): Double {
            val counts = docs[id] ?: return 0.0
            val length = lengths.getValue(id)
            return query.sumOf { term ->
                val tf = counts[term] ?: return@sumOf 0.0
                val n = df.getValue(term)
                val idf = kotlin.math.ln(1 + (docs.size - n + 0.5) / (n + 0.5))
                idf * tf * 2.2 / (tf + 1.2 * (0.25 + 0.75 * length / avg))
            }
        }
    }

    @Volatile private var indexed: Pair<List<EmergencyGuide>, GuideIndex>? = null

    private fun guideIndex(guides: List<EmergencyGuide>): GuideIndex {
        indexed?.let { (key, index) -> if (key === guides) return index }
        val docs = guides.filterNot { it.id in keywordOnly }.associate { guide ->
            guide.id to textTerms(listOf(guide.title, guide.keywords.joinToString(" "), guide.plainText()).joinToString(" "))
                .groupingBy { it }.eachCount()
        }
        val lengths = docs.mapValues { (_, counts) -> counts.values.sum() }
        val df = HashMap<String, Int>()
        docs.values.forEach { counts -> counts.keys.forEach { df[it] = (df[it] ?: 0) + 1 } }
        return GuideIndex(docs, lengths, df, lengths.values.average().coerceAtLeast(1.0)).also { indexed = guides to it }
    }

    private val textStopwords = ("a an the and or but if of to in on at for with from by is are was were be been being it its this that these " +
        "those i me my we our you your he him his she her they them their what how do does did can could should would will just now not no so " +
        "very really then than there here as up down out over into about after before when where who which while because get got go going").split(' ').toSet()

    /** Lower-case words of three letters or more, without common words, with plain suffixes cut ("swelling" -> "swell"). */
    private fun textTerms(text: String): List<String> = Regex("[a-z]+").findAll(text.lowercase(Locale.ROOT)).map { it.value }
        .filter { it.length > 2 && it !in textStopwords }
        .map { word -> listOf("ing", "ed", "es", "s", "ly").firstOrNull { word.length > it.length + 3 && word.endsWith(it) }
            ?.let { word.dropLast(it.length) } ?: word }
        .toList()

    /** A guide this short, or with one section, leaves the model little to work from. */
    private const val SHORT_GUIDE_CHARS = 600
    /** Far from help: a trail, a tent, no signal, hours from the car. */
    private val outdoorContext = Regex(
        "(?i)\\b(trail|hik\\w*|trek\\w*|ridge|camp\\w*|tent|no signal|no reception|no phone|hours? from (?:the|our) (?:car|trailhead|road)|" +
            "village|remote|wilderness|backcountry|summit|mountains?)\\b",
    )
    private val injuryGuides = setOf("fractures-sprains", "severe-bleeding", "bleeding-control", "burns", "head-injury", "snakebite",
        "spider-bites", "insect-stings", "animal-bites", "tick-bites")
    private val coldGuides = setOf("hypothermia", "frostbite", "extreme-cold")

    /**
     * Guides packed beside [best] for the same answer. A short or single-section guide (the
     * four-sentence bleeding guide) gets the next-ranked guide of its category (bleeding control);
     * an injury or bite far from help gets the outdoors guide (flag down help, send two people,
     * keep warm), and cold far from help gets the shelter guide. The model once answered a
     * hypothermia case on a dark trail with "move into a warm room".
     */
    fun companions(question: String, best: EmergencyGuide, ranked: List<Pair<EmergencyGuide, Int>>, guides: List<EmergencyGuide>): List<EmergencyGuide> {
        val out = mutableListOf<EmergencyGuide>()
        if (best.plainText().length < SHORT_GUIDE_CHARS || sections(best).size == 1) {
            ranked.firstOrNull { (guide, _) -> guide.id != best.id && guide.category == best.category }?.let { out += it.first }
        }
        if (best.id == "drowning" && throughIce.containsMatchIn(question))
            guides.firstOrNull { it.id == "hypothermia" }?.takeIf { out.none { o -> o.id == it.id } }?.let { out += it }
        if (outdoorContext.containsMatchIn(question)) {
            val extra = when (best.id) {
                in injuryGuides -> "injured-outdoors"
                in coldGuides -> "emergency-shelter"
                else -> null
            }
            guides.firstOrNull { it.id == extra }?.takeIf { it.id != best.id && out.none { o -> o.id == it.id } }?.let { out += it }
        }
        return out
    }

    private val throughIce = Regex("(?i)\\bthrough (?:the )?ice\\b")

    /** The guides to pack for the answer, primary first; empty when no guide matches. */
    fun pack(question: String, guides: List<EmergencyGuide>): List<EmergencyGuide> {
        val ranked = rank(question, guides)
        val best = ranked.firstOrNull()?.first ?: return emptyList()
        return listOf(best) + companions(question, best, ranked, guides)
    }

    private fun normalize(text: String) = text.lowercase(Locale.ROOT).replace('’', '\'')
        .replace(Regex("[^\\p{L}\\p{N}' ]+"), " ").replace(Regex("\\s+"), " ").trim()

    private val actionHeading = Regex("(?i)(first aid|what to do|what not to do|^if |\\bif you|treat|steps|how to|during|after|stay|seek|call|do not|don't|warning|help|care|strategy|thrusts?|blows|heimlich|boil|disinfect|warm|cool|rescue|signal|protect|evacuate|get to|move to|drop)")
    private val avoidHeading = Regex("(?i)(not to do|do not|don't|avoid)")
    private val signsHeading = Regex("(?i)(signs?|symptoms?|look out|warning signs|recogni[sz]e)")
    private val asksSigns = Regex("(?i)\\b(signs?|symptoms?|how do i know|recogni[sz]e|look like)\\b")

    /** Signs that a guide sends to a professional, not to home care ("unable to tolerate any weight"). */
    private val redFlags = Regex(
        "(?i)\\b((can'?t|cannot|can not|unable to|won'?t) (put|bear|take|tolerate|stand) (any )?weight|" +
            "(can'?t|cannot|unable to) (walk|stand|move (it|his|her|their))|bone (is )?(sticking|poking|showing) (out|through)|" +
            "deformed|crooked|bent|misshapen|out of place|odd angle|looks wrong|numb|tingling|severe pain|very swollen|" +
            "swelling (is )?getting worse)\\b",
    )
    private val seeksCare = Regex(
        "(?i)\\b(see a (health care provider|doctor)|seek (medical|emergency|immediate)|get (medical|emergency) (help|care)|" +
            "call (911|112|999|your local emergency number)|emergency (room|department))\\b",
    )

    /**
     * Clinical words for the signs people type. The heat guide lists "confusion" and "loss of
     * consciousness" under heat stroke; the person writes "confused" and "collapsed", and ranked on
     * the question's own words the section that fits the case lost to the guide's introduction.
     * Keys are matched as whole words in the question; the values join the ranking query.
     */
    private val layLexicon = linkedMapOf(
        "confus\\w*" to "confusion",
        "collaps\\w*|passed out|pass(?:es|ing)? out|faint\\w*|blacked out|unconscious" to "fainting loss of consciousness unconscious",
        "threw up|throw(?:s|ing|n)? up|being sick|been sick|puk\\w*" to "vomiting nausea",
        "stumbl\\w*|stagger\\w*|wobbl\\w*|unsteady" to "balance coordination",
        "(?:stopped|not|isn'?t|no longer|barely|hardly) sweating" to "dry skin",
        "swollen|swell\\w*|puff(?:y|ed)" to "swelling",
        "broke|broken|cracked|snapped" to "fracture fractured broken bone",
        "can'?t breathe|cannot breathe|out of breath|struggling to breathe|gasping|short of breath" to "shortness of breath",
        "diarrhoea" to "diarrhea",
        "bit|bitten" to "bite",
        "stung" to "sting",
    ).map { (lay, clinical) -> Regex("(?i)\\b(?:$lay)\\b") to clinical }

    /** The question's words for ranking, with the clinical names of the signs it describes. */
    private fun rankingQuery(question: String): List<String> {
        val clinical = layLexicon.filter { (lay, _) -> lay.containsMatchIn(question) }.joinToString(" ") { it.second }
        return xyz.fieldatlas.research.QueryTerms.of(question) + xyz.fieldatlas.research.QueryTerms.of(clinical)
    }

    /** Signs that make the case the guide's emergency, not its mild form (heat stroke, not heat cramps). */
    private val severeSigns = Regex(
        "(?i)\\b(confus\\w*|collaps\\w*|unconscious|unresponsive|passed out|pass(?:es|ing)? out|faint\\w*|stumbl\\w*|stagger\\w*|" +
            "slurr\\w*|seiz\\w*|(?:not|isn'?t|stopped) breathing|drowsy|blue lips)\\b",
    )
    /** An instruction to get emergency help now, however the guide words it. */
    private val emergencyInstruction = Regex(
        "(?i)\\b(call (?:911|112|999|000|an ambulance|emergency services|(?:your|the) local emergency number|for help)|" +
            "medical emergency|life[- ]threatening|fatal|" +
            "(?:go down|descend|seek (?:medical |emergency )?(?:help|attention|care)|get (?:medical |emergency )?help|" +
            "get (?:\\w+ )?to (?:a |the )?hospital|" +
            // "Get the person proper medical attention as soon as possible" (hypothermia).
            "(?:seek|get)(?: \\w+){0,3} (?:medical|emergency) (?:help|attention|care|assistance))" +
            "[^.!?\\n]{0,40}\\b(?:immediately|right away|at once|now|as soon as possible)|" +
            "(?:immediately|right away) (?:go down|descend|seek|get|call))\\b",
    )
    /** "Call 911 if the seizure lasts longer than 5 minutes" is a condition to check, not the instruction for every case. */
    private val conditional = Regex("(?i)\\b(if|unless|when|whenever|in case)\\b")
    private val sentenceBreak = Regex("(?<=[.!?])\\s+|\\n")

    /**
     * Whether any one sentence tells the reader to get emergency help without a condition on it.
     * The sentence must say what to do: "Hypothermia is a medical emergency" describes, and ranked
     * as an instruction it led the symptom list ahead of the guide's warming steps.
     */
    private fun urgentNow(text: String): Boolean = sentenceBreak.split(text).any {
        emergencyInstruction.containsMatchIn(it) && !conditional.containsMatchIn(it) && imperative.containsMatchIn(it)
    }

    /** Whether a sentence tells the reader when or how to get help, as opposed to naming the emergency. */
    private fun tellsToGetHelp(text: String): Boolean = sentenceBreak.split(text).any {
        (emergencyInstruction.containsMatchIn(it) || seeksCare.containsMatchIn(it)) && imperative.containsMatchIn(it)
    }

    /** A sentence that tells the reader to do something, as opposed to one that describes the illness. */
    private val imperative = Regex(
        "(?i)(?:^|[.!?:]\\s+|\\n|- |\\d+\\. )(?:do not|don'?t|never|always|call|move|get|give|offer|apply|remove|keep|stay|seek|cool|" +
            "loosen|go|drink|send|check|wash|use|put|hold|press|cover|elevate|rest|stop|avoid|lie|sit|turn|tilt|start|continue|time|" +
            "wait|follow|treat|place|wrap|take|run|leave|ask|tell|look|listen|find|carry|bring|learn|remember|protect|immobili[sz]e|" +
            "splint|raise|lower|warm|signal|build|boil|add|let|store|have|make|flag|minimi[sz]e|control|eat|stand|lay|try|contact|" +
            "rub|capture|remain|talk|wipe|scrape|push|pinch|watch|open|tap|position|rinse|flush|soak|cut|tie|shield|descend|climb|" +
            "spend|drop|crouch|crawl|evacuate|head|shelter|unplug|close|fill|pack|wear|monitor|note|reduce|encourage|perform|begin|" +
            "administer|pour|limit|consult|allow|help|support|lift|lean|breathe|count|repeat|alternate|squeeze|pull|roll)\\b|" +
            "\\b(?:should|must|need to|needs to)\\b",
    )
    /** "What is CPR?", "Summary": the guide's definition, which answers only a question for one. */
    private val definitionHeading = Regex("(?i)^(what (is|are|does)\\b|summary$|overview$|background$)")
    private val asksDefinition = Regex(
        "(?i)\\bwhat (?:is|are|does) (?!(?:the )?(?:best|fastest|quickest|right|safest|first|most|correct|proper) )(?:a |an |the )?[\\p{L}' -]{2,30}(?: mean)?\\s*[?.!]*\\s*$|" +
            "\\bdefin(?:e|ition)\\b|\\bmeaning of\\b",
    )
    /** "When should I start worrying?": the guide's get-help-if list is the answer. */
    private val asksWhenToWorry = Regex(
        "(?i)\\b(when (?:should|do|would|must) (?:i|we|you|they|someone) (?:start )?(?:worr\\w*|be worried|be concerned|get help|need help|" +
            "seek help|call|go to|see a doctor|take (?:him|her|them) (?:in|to))|" +
            "when (?:do|does|would|should) (?:he|she|they|it|someone) need|should (?:i|we) (?:worry|be worried|be concerned|get help|call)|" +
            "when (?:is it|to) (?:time to )?(?:get|seek|call|go)|how (?:do|will|would) (?:i|we) know (?:if|when|whether)|" +
            "(?:is|are) (?:it|this|he|she|they) (?:serious|dangerous|an emergency)|do (?:i|we) need (?:a doctor|a hospital|help|to go)|" +
            "when (?:do|should) (?:i|we) (?:go|get) to (?:a |the )?(?:hospital|doctor|er))\\b",
    )
    /** Prose this long with no list is an article; the lead quotes its one paragraph for the case. */
    private const val LONG_PROSE_CHARS = 1_500
    /** A pointer to a web page or an app inside guide text: nothing an offline reader can follow. */
    private val onlinePointer = Regex(
        "(?i)\\b(?:see|visit|check|go to|download|consult|read|find)\\b[^.!?\\n]*?\\b(?:web ?sites?|web ?pages?|online|internet|" +
            "the (?:[\\p{L}()/ ]+ )?(?:app|site)|an app|apps|www\\.|https?://|\\.gov|\\.org|\\.com)\\b",
    )
    /** The site of a sting or wound is a place on the body. */
    private val bodySite = Regex("(?i)\\b(?:sting|bite|wound|injury|injection|burn|affected) site\\b")

    /**
     * The guide split into citable sections (a heading with its follow-on blocks), in reading
     * order. Each keeps the published wording; list items keep their markers and nesting.
     */
    fun sections(guide: EmergencyGuide): List<Evidence> {
        val merged = mutableListOf<Pair<String, StringBuilder>>()
        for (section in guide.sections) {
            val body = buildString {
                section.paragraphs.forEach { append(it).append('\n') }
                section.steps.forEachIndexed { i, step ->
                    append("  ".repeat(section.stepLevel(i) - 1)).append(if (section.numbered) "${i + 1}. " else "- ").append(step).append('\n')
                }
            }
            val last = merged.lastOrNull()
            when {
                section.heading.isBlank() && last != null -> last.second.append(body)
                // A heading with nothing under it ("First Aid") introduces the next section.
                last != null && last.second.isBlank() -> merged[merged.lastIndex] = "${last.first}: ${section.heading}" to StringBuilder(body)
                else -> merged += section.heading to StringBuilder(body)
            }
        }
        return merged.filter { it.second.isNotBlank() || it.first.isNotBlank() }.mapIndexedNotNull { index, (heading, body) ->
            val text = listOf(heading, body.toString().trim()).filter(String::isNotBlank).joinToString("\n")
            if (text.isBlank()) return@mapIndexedNotNull null
            Evidence(
                documentId = "emergency:${guide.id}",
                chunkId = "emergency:${guide.id}:$index",
                title = if (heading.isBlank()) guide.title else "${guide.title}: $heading",
                source = guide.source.url,
                text = text,
                score = 1.0,
                matchedBy = "emergency guide",
            )
        }
    }

    /**
     * Sections for the model within [maxTokens]: what-to-do sections first (or the signs, when
     * the question asks how to recognise the emergency), then the rest. The best section is
     * always kept, so even a slow phone gets the core steps.
     */
    fun select(question: String, guide: EmergencyGuide, maxTokens: Int): List<Evidence> = select(question, listOf(guide), maxTokens)

    /** A companion's section needs this much more than a primary's to be read before it. */
    private const val COMPANION_PENALTY = 2.5

    /**
     * Sections of the primary guide and its [companions] under one budget. The primary's best
     * section always leads (it is the quoted excerpt), then sections of every guide by priority,
     * a companion's handicapped so the guide that was asked for is read first.
     */
    fun select(question: String, guides: List<EmergencyGuide>, maxTokens: Int): List<Evidence> {
        val order = guides.flatMapIndexed { index, guide ->
            prioritised(question, guide).map { (section, priority) -> Triple(section, priority - if (index > 0) COMPANION_PENALTY else 0.0, index) }
        }.sortedByDescending { it.second }
        val head = order.filter { it.third == 0 }.maxByOrNull { it.second } ?: return emptyList()
        val kept = mutableListOf<Evidence>()
        var used = 0
        for ((section, _, _) in listOf(head) + order.filter { it !== head }) {
            val cost = xyz.fieldatlas.research.TokenEstimate.of(section.text) + 20
            if (kept.isNotEmpty() && used + cost > maxTokens) continue
            kept += section
            used += cost
        }
        // Most important first: a small model leans on the first record it reads.
        return kept
    }

    /** Every section of [guide] with its priority for [question], in ranking order. */
    private fun prioritised(question: String, guide: EmergencyGuide): List<Pair<Evidence, Double>> {
        val all = sections(guide)
        if (all.isEmpty()) return emptyList()
        val ranked = xyz.fieldatlas.research.Bm25.rank(all, rankingQuery(question))
        val wantsSigns = asksSigns.containsMatchIn(question)
        val flagged = redFlags.containsMatchIn(question)
        val severe = severeSigns.containsMatchIn(question)
        val worried = asksWhenToWorry.containsMatchIn(question)
        val definition = asksDefinition.containsMatchIn(question)
        fun priority(e: Evidence): Double {
            val heading = e.title.substringAfter(": ", "")
            val signs = signsHeading.containsMatchIn(heading)
            val action = actionHeading.containsMatchIn(heading)
            val list = e.text.contains("\n- ") || e.text.contains("\n1. ")
            // Guides state the overall procedure early ("General strategy"), details later. Any
            // stronger a prior and the heat guide's cramps come before its heat stroke; any weaker
            // and the seizure guide's "call 911 if" list comes before its steps.
            val position = e.chunkId.substringAfterLast(':').toInt()
            return e.score + 2.0 * (1.0 - position.toDouble() / all.size) +
                // The main steps come before the "what not to do" list when only one fits.
                (if (action) (if (avoidHeading.containsMatchIn(heading)) 2.0 else 3.0) else 0.0) +
                (if (list) 1.0 else 0.0) +
                (if (signs) (if (wantsSigns) 4.0 else -1.0) else 0.0) +
                // "He cannot put weight on it": the guide's when-to-get-care section leads, not home care.
                (if (flagged && seeksCare.containsMatchIn(e.text)) 6.0 else 0.0) +
                // Confused and collapsed: the section that says to call for help now, not the mild
                // form's "sips of water". A "call 911 if" condition is not that instruction.
                (if (severe && urgentNow(e.text)) 4.0 else 0.0) +
                // "When should I worry?" is answered by the guide's get-help-if list.
                (if (worried && tellsToGetHelp(e.text)) 5.0 else 0.0) +
                // A headingless opening with no list and nothing to do is the guide's introduction;
                // quoted first, it once became the whole visible answer to a heat stroke.
                (if (heading.isBlank() && !list && all.size > 1 && !imperative.containsMatchIn(e.text)) -3.0 else 0.0) +
                // "What is CPR?" explains the term; it leads only when the term is what was asked.
                (if (definitionHeading.containsMatchIn(heading) && !signs && !action && !definition) -2.0 else 0.0)
        }
        return ranked.map { it to priority(it) }
    }

    /**
     * The key section exactly as published, shown by the app under the model's answer, so the
     * source wording is always on screen even if the model condenses or garbles a step.
     */
    fun officialSteps(section: Evidence, citation: Int, publisher: String, question: String = ""): String = buildString {
        append("**Source excerpt from ").append(publisher).append(", as published** [S").append(citation).append("]\n\n")
        // sections() puts the heading on the first line; show it as a caption, then the body.
        val heading = section.title.substringAfter(": ", "")
        val lines = section.text.lines()
        val body = if (heading.isNotBlank() && lines.firstOrNull() == heading) lines.drop(1) else lines
        val blocks = body.filter(String::isNotBlank)
        // An article with no list (altitude sickness, drowning) quoted whole pushes the answer for
        // this case off the screen: only the paragraph for the case is quoted, and the citation
        // still opens the whole guide.
        val article = blocks.size >= 3 && blocks.none(::isListItem) && blocks.sumOf { it.length } > LONG_PROSE_CHARS
        val quoted = if (article) {
            val best = bestParagraph(question, blocks)
            listOf((if (best > 0) "… " else "") + blocks[best] + (if (best < blocks.lastIndex) " …" else ""))
        } else {
            if (body !== lines) append("_").append(heading).append("_\n\n")
            blocks
        }
        for (line in quoted) {
            val kept = withoutOnlinePointers(line) ?: continue
            append(kept).append(if (isListItem(kept)) "\n" else "\n\n")
        }
    }.trimEnd()

    private fun isListItem(line: String) = line.trimStart().let { it.startsWith("- ") || Regex("^\\d+\\. ").containsMatchIn(it) }

    /**
     * The paragraph of an article that fits the case: the question's words and the clinical names
     * of its signs, an instruction over a description, and with a severe sign the paragraph that
     * says to get help now ("Go down immediately and seek medical attention").
     */
    private fun bestParagraph(question: String, paragraphs: List<String>): Int {
        val severe = severeSigns.containsMatchIn(question)
        // Evidence rejects blank fields, and these records exist only to be ranked, so the
        // paragraph's ordinal stands in for a title and the ranker's name for a source.
        val ranked = xyz.fieldatlas.research.Bm25.rank(
            paragraphs.mapIndexed { index, text -> Evidence("paragraph", "paragraph:$index", "Paragraph ${index + 1}", "bm25", text, 0.0) },
            rankingQuery(question),
        )
        val scores = ranked.associate { it.chunkId.substringAfterLast(':').toInt() to it.score }
        return paragraphs.indices.maxByOrNull { index ->
            scores.getValue(index) + (if (imperative.containsMatchIn(paragraphs[index])) 1.0 else 0.0) +
                (if (severe && urgentNow(paragraphs[index])) 4.0 else 0.0)
        } ?: 0
    }

    /**
     * The line without any sentence that sends the reader to a web page or an app ("for more
     * information, see the CDC site"): the reader is offline. Null when nothing else was in it.
     */
    private fun withoutOnlinePointers(line: String): String? {
        val marker = Regex("^\\s*(?:- |\\d+\\. )").find(line)?.value.orEmpty()
        val sentences = Regex("(?<=[.!?])\\s+").split(line.substring(marker.length))
        val kept = sentences.filterNot { onlinePointer.containsMatchIn(it) && !bodySite.containsMatchIn(it) }
        if (kept.size == sentences.size) return line
        return (marker + kept.joinToString(" ")).takeIf { it.any(Char::isLetter) }
    }

    /** Written by the app after the model's answer, never by the model. */
    fun safetyNote(guide: EmergencyGuide): String = buildString {
        append("If this is an emergency, call your local emergency number first (112 works on mobile phones in many countries). ")
        // Written for someone who may have no signal at all.
        append("No signal? Try the emergency call anyway: in many countries it can go through another carrier's network. ")
        append("If nothing connects, send someone for help and keep following these steps. ")
        append("Steps are from ${guide.source.publisher}; tap a citation to read the guide.")
        if (guide.region == "US") append(" The guide was written for the United States; numbers and some advice (such as local snakes) may differ where you are.")
        if (guide.region == "UK") append(" The guide was written for the United Kingdom; numbers (999, 111) and services may differ where you are.")
    }

    /** The guide as a citable source; its text is the published wording. */
    fun evidence(guide: EmergencyGuide) = Evidence(
        documentId = "emergency:${guide.id}",
        chunkId = "emergency:${guide.id}:0",
        title = guide.title,
        source = guide.source.url,
        text = guide.plainText(),
        score = 1.0,
        matchedBy = "emergency guide",
    )

    /**
     * The guide as published. Only formatting is added; every sentence is the source's own.
     * [modelUnavailable] preserves the explanation for callers showing an unavailable-model fallback.
     */
    fun answer(guide: EmergencyGuide, modelUnavailable: Boolean = true, question: String = "", cautious: Boolean = false): String = buildString {
        append(GuideAnswerCheck.callLine(question, guideId = guide.id, cautious = cautious))
        append(if (modelUnavailable) "_The answer model isn't available right now, so this is the guide as published._\n\n"
            else "_For safety, this is the guide as published, without a model rewrite._\n\n")
        if (guide.id in setOf("poisoning", "poisoning-steps") && batteryIngestion.containsMatchIn(question)) {
            append("_This saved reference covers poisoning generally; it is not a battery-specific treatment guide. Contact local emergency services or poison control immediately._\n\n")
        }
        append(published(guide))
    }.trimEnd()

    /** The whole guide as published, cited as [S1]; only formatting is added. */
    fun published(guide: EmergencyGuide): String = buildString {
        append("_${guide.title}: shown as published by ${guide.source.publisher} [S1]")
        if (guide.region == "US") append(". This guide was written for the United States; numbers and some advice (such as local snakes) may differ where you are")
        if (guide.region == "UK") append(". This guide was written for the United Kingdom; numbers (999, 111) and services may differ where you are")
        append("._\n\n")
        for (section in guide.sections) {
            if (section.heading.isNotBlank()) append("#".repeat((section.level + 2).coerceAtMost(4))).append(' ').append(section.heading).append("\n\n")
            for (paragraph in section.paragraphs) append(paragraph).append("\n\n")
            section.steps.forEachIndexed { i, step ->
                append("  ".repeat(section.stepLevel(i) - 1))
                append(if (section.numbered) "${i + 1}. " else "- ").append(step).append('\n')
            }
            if (section.steps.isNotEmpty()) append('\n')
        }
        // The source's own credit line, as its licence asks (the NHS's Open Government Licence, CDC's no-endorsement note).
        guide.source.attribution?.let { append("_").append(it).append("_\n") }
    }.trimEnd()
}
