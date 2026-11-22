package com.nuvio.app.features.profiles

/**
 * Picture avatars that ship with the app. They are drawn as emoji on a coloured disc, so they
 * work offline and when the remote Nuvio avatar catalog (Supabase `get_avatar_catalog`) is
 * unavailable because NUVIO_SUPABASE_URL / NUVIO_SUPABASE_ANON_KEY are not configured.
 */
internal const val BUILT_IN_AVATAR_ID_PREFIX = "emoji_"

private fun builtIn(
    slug: String,
    name: String,
    emoji: String,
    category: String,
    order: Int,
    background: String,
) = AvatarCatalogItem(
    id = BUILT_IN_AVATAR_ID_PREFIX + slug,
    displayName = name,
    category = category,
    sortOrder = order,
    bgColor = background,
    emoji = emoji,
)

internal val BuiltInAvatarCatalog: List<AvatarCatalogItem> = listOf(
    builtIn("clapper", "Clapperboard", "🎬", "cinema", 0, "#37474F"),
    builtIn("popcorn", "Popcorn", "🍿", "cinema", 1, "#E53935"),
    builtIn("masks", "Drama", "🎭", "cinema", 2, "#6A1B9A"),
    builtIn("movie_camera", "Movie camera", "🎥", "cinema", 3, "#00695C"),
    builtIn("trophy", "Trophy", "🏆", "cinema", 4, "#F9A825"),
    builtIn("star", "Star", "⭐", "cinema", 5, "#3949AB"),

    builtIn("fox", "Fox", "🦊", "animals", 0, "#EF6C00"),
    builtIn("panda", "Panda", "🐼", "animals", 1, "#546E7A"),
    builtIn("tiger", "Tiger", "🐯", "animals", 2, "#F9A825"),
    builtIn("lion", "Lion", "🦁", "animals", 3, "#FB8C00"),
    builtIn("koala", "Koala", "🐨", "animals", 4, "#78909C"),
    builtIn("frog", "Frog", "🐸", "animals", 5, "#2E7D32"),
    builtIn("octopus", "Octopus", "🐙", "animals", 6, "#AD1457"),
    builtIn("owl", "Owl", "🦉", "animals", 7, "#6D4C41"),
    builtIn("penguin", "Penguin", "🐧", "animals", 8, "#0277BD"),
    builtIn("dog", "Dog", "🐶", "animals", 9, "#8D6E63"),
    builtIn("cat", "Cat", "🐱", "animals", 10, "#5E35B1"),
    builtIn("rabbit", "Rabbit", "🐰", "animals", 11, "#D81B60"),
    builtIn("bear", "Bear", "🐻", "animals", 12, "#4E342E"),
    builtIn("unicorn", "Unicorn", "🦄", "animals", 13, "#8E24AA"),
    builtIn("turtle", "Turtle", "🐢", "animals", 14, "#00897B"),
    builtIn("butterfly", "Butterfly", "🦋", "animals", 15, "#1E88E5"),

    builtIn("robot", "Robot", "🤖", "characters", 0, "#455A64"),
    builtIn("alien", "Alien", "👽", "characters", 1, "#43A047"),
    builtIn("ghost", "Ghost", "👻", "characters", 2, "#5C6BC0"),
    builtIn("ninja", "Ninja", "🥷", "characters", 3, "#212121"),
    builtIn("wizard", "Wizard", "🧙", "characters", 4, "#4527A0"),
    builtIn("vampire", "Vampire", "🧛", "characters", 5, "#B71C1C"),
    builtIn("hero", "Hero", "🦸", "characters", 6, "#C62828"),
    builtIn("cowboy", "Cowboy", "🤠", "characters", 7, "#A1887F"),
    builtIn("cool", "Cool", "😎", "characters", 8, "#F57C00"),
    builtIn("pumpkin", "Pumpkin", "🎃", "characters", 9, "#E65100"),
    builtIn("skull", "Skull", "💀", "characters", 10, "#37474F"),

    builtIn("rocket", "Rocket", "🚀", "fun", 0, "#283593"),
    builtIn("planet", "Planet", "🪐", "fun", 1, "#6A1B9A"),
    builtIn("moon", "Moon", "🌙", "fun", 2, "#1A237E"),
    builtIn("bolt", "Lightning", "⚡", "fun", 3, "#F9A825"),
    builtIn("fire", "Fire", "🔥", "fun", 4, "#D84315"),
    builtIn("rainbow", "Rainbow", "🌈", "fun", 5, "#00ACC1"),
    builtIn("gamepad", "Gamepad", "🎮", "fun", 6, "#3949AB"),
    builtIn("guitar", "Guitar", "🎸", "fun", 7, "#BF360C"),
    builtIn("pizza", "Pizza", "🍕", "fun", 8, "#C62828"),
    builtIn("donut", "Donut", "🍩", "fun", 9, "#EC407A"),
)
