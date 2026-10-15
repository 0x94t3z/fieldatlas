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
            "choking|lost|stranded|injured|hurt|unconscious|not breathing|can'?t breathe|collapsed|prepare|(?:should|do) (?:i|we)|is it safe|purify|disinfect|boil|safest)\\b",
    )
    /** Words people use that the sources' keywords miss. */
    private val synonyms = mapOf(
        "cpr" to listOf("cardiopulmonary", "no pulse", "collapsed", "isn't breathing", "is not breathing", "stopped breathing",
            "not breathing", "no breathing", "doesn't breathe", "does not breathe", "won't wake up", "not responding"),
        "choking" to listOf("choke", "something stuck in throat", "can't breathe", "cant breathe"),
        "severe-bleeding" to listOf("bleed", "bleeding badly"),
        "burns" to listOf("burned", "burnt", "scalded", "hot pan", "hot stove", "boiling water", "hot oil", "burned hand", "burned my", "burned her", "burned his"),
        "fractures-sprains" to listOf("broken leg", "broken arm", "broken ankle", "broken wrist", "sprained",
            "twisted ankle", "twisted my ankle", "rolled my ankle", "swollen ankle", "ankle is swollen",
            "can't put weight", "cant put weight", "cannot put weight", "twisted his ankle", "twisted her ankle",
            "rolled his ankle", "rolled her ankle", "wrist is swollen", "swollen wrist", "swollen and bent", "bent wrist",
            "arm is bent", "leg is bent", "looks broken", "might be broken", "may be broken", "think it's broken", "broken bone",
            // "I think my friend broke her ankle on the trail": the trail words alone pointed to the outdoors guide.
            "broke her ankle", "broke his ankle", "broke my ankle", "broke an ankle", "broke her leg", "broke his leg", "broke my leg",
            "broke her arm", "broke his arm", "broke my arm", "broke her wrist", "broke his wrist", "broke my wrist", "broke a bone"),
        "lost" to listOf("i'm lost", "i am lost", "we're lost", "we are lost", "got lost", "lost in the woods", "lost hiking", "lost on a hike"),
        "safe-drinking-water" to listOf("drink", "drinkable", "stream water", "river water", "lake water", "purify water", "disinfect water", "boil water"),
        "signaling" to listOf("signal for help", "rescue signal", "attract rescuers", "call for help without signal"),
        "heat-illness" to listOf("heatstroke", "overheated", "stopped sweating", "not sweating"),
        "stroke" to listOf("face is drooping", "drooping face", "face droops"),
        "poisoning" to listOf("swallowed bleach", "swallowed some bleach", "drank bleach", "swallowed poison", "swallowed pills",
            "swallowed some pills", "swallowed medicine",
            "swallowed detergent", "swallowed antifreeze"),
        "anaphylaxis" to listOf("throat is swelling", "throat swelling", "swollen throat", "tongue is swelling", "lips are swelling"),
        "head-injury" to listOf("hit his head", "hit her head", "hit my head", "hit their head", "banged his head", "banged her head", "bumped his head", "bumped her head"),
        // Falling through ice: the water rescue first (from safe ground), then warming the person.
        "drowning" to listOf("fell through the ice", "fallen through the ice", "falls through the ice", "through the ice"),
        "hypothermia" to listOf("fell in a cold", "fell into a cold", "cold river", "cold lake", "freezing water",
            "icy water", "through the ice",
            "body temperature", "shivering", "uncontrollable shivering"),
        "altitude-sickness" to listOf("mountain sickness", "high altitude"),
        "animal-bites" to listOf("bitten by a dog", "monkey bite", "bat bite", "dog bit", "bit by a dog", "cat bit", "bit by a cat", "monkey bit", "bit by a monkey",
            "monkey scratched", "scratched by a monkey", "dog scratched", "scratched by a dog", "cat scratched", "scratched by a cat",
            "bat scratched", "scratched by a bat", "animal scratch"),
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
            "fingers are white", "toes are white", "fingers are numb", "toes are numb", "numb fingers", "numb toes", "waxy skin"),
        "carbon-monoxide" to listOf("gas heater", "space heater", "kerosene heater", "paraffin heater", "generator", "charcoal",
            "camp stove", "gas stove", "co alarm", "co detector", "carbon monoxide alarm", "carbon monoxide detector"),
        // An explicit rehydration request or diarrheal symptoms must not become an invented
        // home-mixing recipe. The saved guide describes fluid loss and when to seek care.
        "dehydration" to listOf("diarrhea", "diarrhoea", "rehydration drink", "rehydration solution", "rehydration mixture", "rehydration mix"),
    )

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
            "my (fingers?|toes?|hands?|feet|foot|legs?|arms?|skin|face|ears?|nose|eyes?|knees?|ankles?|wrists?|lips?)|" +
            // A bystander describing a stranger, in the present: "a man has collapsed and is not breathing".
            "(a|an|the|this) (man|woman|person|child|kid|boy|girl|baby|hiker|driver|player|swimmer|runner) (has|is|was|just)|" +
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
            "unresponsive|not responding|won['’]?t wake up|(?:$PERSON)\\b(?:[^.?!]{0,60}? and)? (?:isn['’]?t|wasn['’]?t) responding)\\b(?! to\\b)",
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
        "(?i)^\\s*(?:what (?:causes|caused|is|are|was|were)\\b(?! the (?:\\w+ ){0,2}(?:ways?|things?) to\\b)|" +
            "why (?:do|does|did|is|are|was|were)\\b|" +
            "how (?:do|does|did|is|are) (?!i\\b|we\\b|you\\b|my\\b|our\\b|someone\\b|somebody\\b)[\\p{L}' -]{2,40}? " +
            "(?:form|forms|formed|work|works|happen|happens|occur|occurs|start|starts|spread|spreads|develop|develops|differ|differs|get made|made|" +
            "make|makes|keep|keeps|stop|stops|kill|kills|remove|removes)\\b)",
    )

    private val safetyWords = Regex("(?i)\\b(?:safe|safely|safety|safest)\\b")

    fun match(question: String, guides: List<EmergencyGuide>): EmergencyGuide? = rank(question, guides).firstOrNull()?.first

    // Quoting a reference about safety is different from asking for immediate help.
    // A personal situation always takes priority, including mixed research/help requests.
    private val sourceResearch = Regex(
        "(?i)\\b(?:what (?:do|does|did) (?:the |my |our )?(?:saved |offline )?" +
            "(?:[\\p{L}-]+ ){0,4}(?:guides?|sources?|references?|articles?|papers?) say|" +
            "(?:summarize|summarise|compare|review) (?:the |my |our )?(?:saved |offline )?" +
            "(?:[\\p{L}-]+ ){0,4}(?:guides?|sources?|references?|articles?|papers?))\\b",
    )

    // "What did the PREDIMED trial find?" asks about evidence, not for help, unless a person is in it.
    private val evidenceResearch = Regex("(?i)\\b(?:trials?|stud(?:y|ies)|evidence|proven|research(?:ers)?|meta-analys\\w*|retract\\w*|" +
        "peer[- ]reviewed|statistic\\w*|epidemiolog\\w*)\\b")

    // A place's tap water is its supply, answered by that place's travel guide ("Stay healthy").
    // The treatment guide is for water someone has to make safe, or a supply an event has spoiled.
    private val tapWater = Regex("(?i)\\btap water\\b")
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
    private val otherBreathingCause = Regex("(?i)\\b(chest|heart|altitude|elevation|trek\\w*|everest|summit|mountain|asthma\\w*|inhaler|" +
        "smoke|fumes|panic\\w*|stairs)\\b")
    // "Is choking", "swallowed a nut": something in the airway, said outright. It stays anaphylaxis when the
    // person names the allergy, when the trigger is a sting (nothing was swallowed), or with swelling, hives or a
    // rash, which only an allergy shows.
    private val saysChoking = Regex("(?i)\\bchok\\w*|\\bswallow\\w*\\s+(?:a|an|the|some|his|her|their|my|one)\\s+\\p{L}+")
    private val allergyOnlySign = Regex("(?i)\\b(hives|rash\\w*|welts?|itch\\w*|swollen|swelling|puff\\w*|blotch\\w*)\\b")
    // An allergic trigger with airway or swelling signs is anaphylaxis, not a mild sting or choking.
    private val allergyTrigger = Regex("(?i)\\b(stung|stings?|bee|bees|wasps?|hornets?|insect|peanuts?|nuts?|shellfish|allerg\\w*)\\b")
    private val allergySign = Regex("(?i)\\b(wheez\\w*|(?:trouble|difficulty|problems?|struggling) (?:to )?breath\\w*|hard to breathe|" +
        "can'?t breathe|cannot breathe|short(?:ness)? of breath|hives|throat (?:is |feels )?(?:tight|closing)|chest (?:is |feels )?tight|feel\\w* faint|" +
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
    private val inTheSituation = Regex("(?i)\\b(?:i|i'm|im|i've|we|we're|we've|our|he|he's|she|she's|someone|somebody|" +
        "my (?:friends?|child|children|kids?|sons?|daughters?|wife|husband|partner|boyfriend|girlfriend|dad|mom|mum|mother|father|" +
        "baby|parents|brothers?|sisters?|uncle|aunt|grandma|grandpa|group))\\b")

    private val batteryIngestion = Regex("(?i)\\b(?:swallowed|ingested)\\s+(?:(?:a|the|one|small|tiny)\\s+)?(?:(?:button|coin|disc|disk)\\s+)?batter(?:y|ies)\\b")

    /**
     * Every guide the question names, best first with its score, or nothing when the question is
     * not a request for emergency help. [match] is the head; [companions] reads the rest.
     */
    fun rank(question: String, guides: List<EmergencyGuide>): List<Pair<EmergencyGuide, Int>> {
        val text = " " + normalize(question) + " "
        val words = text.trim().split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        if ((sourceResearch.containsMatchIn(question) || evidenceResearch.containsMatchIn(question)) && !personalSituation.containsMatchIn(question) &&
            !someoneInvolved.containsMatchIn(question)) return emptyList()
        // Extra context must not disqualify an explicit request for help, but a long question
        // that only names a hazard is research, not first aid.
        if (words.size > SHORT_QUESTION_WORDS && !personalSituation.containsMatchIn(question)) return emptyList()
        val dangerous = danger.containsMatchIn(question)
        // A single explanation question about a thing, with no one in it and nothing dangerous happening.
        // "How does a squeeze filter make river water safe? Does it stop viruses?" asks how a thing
        // works: "safe" there describes the water, and a follow-up question about the same thing
        // does not turn it into a request for first aid.
        val dangerBeyondSafety = danger.containsMatchIn(question.replace(safetyWords, " "))
        if (explanation.containsMatchIn(question) && !dangerBeyondSafety && !personalSituation.containsMatchIn(question) &&
            !someoneInvolved.containsMatchIn(question)) return emptyList()
        val heat = heatCollapse.containsMatchIn(question) && someoneFainted.containsMatchIn(question)
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
                score += when {
                    phrase -> term.split(' ').size + 1
                    term in strongWords -> 2
                    dangerous -> 1
                    else -> 0
                } + if (term in own && (' ' in term || term in strongWords || dangerous)) 1 else 0
            }
            // An explicit ingestion needs the saved poison-contact guidance, not a
            // generated treatment. The answer must disclose that this is a general guide.
            if (guide.id == "safe-drinking-water" && tapWater.containsMatchIn(question) && !waterToTreat.containsMatchIn(question))
                return@map Triple(guide, 0, false)
            if (guide.id == "burns" && !heatBurn.containsMatchIn(question) && (marineOrPlantSting.containsMatchIn(question) ||
                    stingOrBite.containsMatchIn(question) && !namedBurn.containsMatchIn(question)))
                return@map Triple(guide, 0, false)
            if (guide.id == "choking" && !airwayObject.containsMatchIn(question) && otherBreathingCause.containsMatchIn(question))
                return@map Triple(guide, 0, false)
            if (guide.id == "poisoning" && (batteryIngestion.containsMatchIn(question) ||
                    wildIngestion.containsMatchIn(question) && poisonSign.containsMatchIn(question))) {
                score += 4
                specific = true
            }
            // One everyday word is not enough on its own: "fever" is a tick-bite keyword, "throat" a
            // choking one, but "my baby has a fever" and "my throat hurts" are neither.
            // A guide's required context (a height for altitude sickness) is itself specific.
            val context = requiredContext[guide.id]?.let { it.containsMatchIn(question) || guide.id == "frostbite" && freezingTemperature(question) }
            val enough = specific || score >= 2 || context == true
            Triple(guide, if (!enough || context == false) 0 else score, specific)
        }
        // The snakebite and flood guides have no CPR step, so a person who is not breathing gets CPR,
        // or the drowning guide's rescue breaths in water. When a choking or swelling airway is the
        // main sign, its own guide stays: those steps are for a person who cannot breathe.
        val top = scored.maxOfOrNull { it.second } ?: 0
        val revive = when {
            !noSignsOfLife.containsMatchIn(question) -> null
            scored.any { (guide, score, specific) -> guide.id in airwayGuides && specific && score > 0 && score == top } -> null
            inWater.containsMatchIn(question) -> "drowning"
            else -> "cpr"
        }
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
                numbOnly && guide.id == "frostbite" && score > 0 -> 1
                heatOnly && guide.id == "heat-illness" -> maxOf(score, 1) + HEAT_BONUS
                outdoors && guide.id == "lightning-backcountry" && score > 0 -> score + LIGHTNING_BONUS
                else -> score
            }
        }.filter { it.second > 0 }
        if (ranked.isEmpty()) return emptyList()
        // A stable sort keeps the first of equal guides first, as the bundled order intends.
        val order = ranked.sortedWith(
            compareByDescending<Pair<EmergencyGuide, Int>> { it.first.id == revive }.thenByDescending { it.second }.thenByDescending { if (it.first.urgent) 1 else 0 },
        )
        // "He fell off the ladder and isn't responding" asks for nothing, but the sign is the emergency.
        // People in trouble often write what is happening, not a question: "tornado siren going off
        // and we're in a motel room". A guide named by its own specific term, with a person in the
        // message, is the help; the research and explanation gates above have already run.
        val namedForSomeone = scored.any { (guide, score, specific) -> guide.id == order.first().first.id && specific && score > 0 } &&
            inTheSituation.containsMatchIn(question)
        return if (words.size <= 4 || dangerous || revive != null || intent.containsMatchIn(question) ||
            personalSituation.containsMatchIn(question) || namedForSomeone) order else emptyList()
    }

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
    fun answer(guide: EmergencyGuide, modelUnavailable: Boolean = true, question: String = ""): String = buildString {
        append("**If this is an emergency, call your local emergency number first** (112 works on mobile phones in many countries).\n\n")
        append(if (modelUnavailable) "_The answer model isn't available right now, so this is the guide as published._\n\n"
            else "_For safety, this is the guide as published, without a model rewrite._\n\n")
        if (guide.id == "poisoning" && batteryIngestion.containsMatchIn(question)) {
            append("_This saved reference covers poisoning generally; it is not a battery-specific treatment guide. Contact local emergency services or poison control immediately._\n\n")
        }
        append(published(guide))
    }.trimEnd()

    /** The whole guide as published, cited as [S1]; only formatting is added. */
    fun published(guide: EmergencyGuide): String = buildString {
        append("_${guide.title}: shown as published by ${guide.source.publisher} [S1]")
        if (guide.region == "US") append(". This guide was written for the United States; numbers and some advice (such as local snakes) may differ where you are")
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
    }.trimEnd()
}
