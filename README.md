<div align="center">
    <a href="https://gameyfin.org">
        <img src="assets/v2/Banner.svg" width="auto" alt="Gameyfin Logo">
    </a>

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=gameyfin_gameyfin&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=gameyfin_gameyfin)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=gameyfin_gameyfin&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=gameyfin_gameyfin)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=gameyfin_gameyfin&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=gameyfin_gameyfin)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=gameyfin_gameyfin&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=gameyfin_gameyfin)
</div>
<div align="center">
    <h2>Gameyfin</h2>
    <h4>Manage your video games.</h4>
    <p>simple / fast / <a href="https://gameyfin.org/blog/2025/12/22/why-gameyfin-is-foss/">FOSS</a></p>
</div>

## Overview

Name and functionality inspired by [Jellyfin](https://jellyfin.org/).

Gameyfin will turn your disorganized collection of video games into a beautiful, easy-to-navigate library that you can
access from any device with a web browser.  
It will automatically scan your game folders, download metadata and cover images, and present everything in a
user-friendly interface.  
Download your game files directly from the web UI, share your library with friends, and enjoy your games like never
before.

### Improved metadata search

This branch improves the manual metadata match dialog. First, you can combine two search results. Sometimes the same game shows
up on two lines, for example when GOG and Steam list different release years. Click **Combine with another result** on
one line, then pick the second line. The provider IDs of both are used together and merged with the usual plugin
priorities, as if Gameyfin had merged them itself.

* Combining is always a manual action. Gameyfin never suggests or links results on its own, so similarly named games
  (such as Mass Effect and Mass Effect Legendary Edition) are not mixed up.
* Each provider supplies one ID per game, so results that share a provider with the first one are disabled.
* No backend, API or database changes for combining. Only the match dialog changes.

#### Screenshots

Here the same game is listed on two lines, because one source has no release year. Each line has a **Combine** button
(link icon), an **Add base game** button (plus icon) and the usual **Pick** button (arrow).

![One game split over two search results](assets/screenshots/improved-metadata-search/split-results.png)

After you press **Combine** on one line, that line and every line from the same source are disabled. Pick the second
line to use both together.

![Combining two results](assets/screenshots/improved-metadata-search/combine-mode.png)

#### Adding a base game to an edition

Special editions often have very little metadata of their own (sometimes just one image and a description). Click
**Select this, then add a base game for missing metadata** on the edition (or on a combined edition), then pick the base
game, using **Combine with another result** again if it is split over several lines.

* The edition's metadata always wins. The base game only fills fields the edition leaves empty, such as the description
  or screenshots. Nothing is overwritten, whatever the plugin priorities are.
* If the base game has no metadata either, nothing is added. Gameyfin never invents metadata.
* The base game is stored in its own table (`GAME_SUPPLEMENT`) and re-applied when metadata is refreshed. Existing tables
  are not changed. Matching a game again without a base game removes it.

##### Screenshots

Search for the edition, then press the plus icon on the edition's line.

![Edition and base game in the search results](assets/screenshots/improved-metadata-search/edition-results.png)

The edition stays selected and you pick the base game from the list.

![Picking the base game](assets/screenshots/improved-metadata-search/pick-base-game.png)

### Inspect metadata sources

This branch adds an **Inspect metadata sources** button (magnifier-list icon) to every result in the manual metadata
match dialog. It shows what each metadata provider returns for that result, side by side, so you can see which source
gives the better information and whether your plugin priorities make sense. Nothing is shown anywhere else, so the rest
of the UI stays uncluttered.

* The inspection covers exactly what picking the result would merge: a pending **Combine** and an **Add base game**
  selection are included (the base game's sources are marked "(base game)").
* One column per source, one row per field. The value that ends up in the game, following the plugin priorities, is
  highlighted in green.
* Values from other sources that differ from the used value are marked with a warning icon and highlighted in yellow.
* Each column header shows how many fields the source provided and how many of those it actually won. A source that
  returned nothing or failed is shown as "No data" or "Error" (hover for the reason) instead of being left out.
* Genres, platforms and so on use their display names, and a meaningless "Unknown" is hidden next to real values.
* Cover, header and screenshots are shown as small thumbnails. Hover one for a bigger preview.
* Click a summary to expand it. Keywords and videos show a count, hover to see them in full.
* **Only show differences** hides the rows where the sources agree.
* Inspecting is read-only: nothing is saved and no game is created or changed.
* The data comes from a new admin-only endpoint, `GameEndpoint.inspectMetadataSources`. There are no database changes.

#### Used in your library

To see which plugin is giving you the most, open a metadata plugin in the plugin administration. It shows how many
metadata fields of your games currently come from that plugin, per field. This is read from the field sources Gameyfin
already stores for every game, so it needs no extra data (admin-only endpoint `GameEndpoint.getMetadataSourceStats`).
Fields you edited yourself are not counted for any plugin.

#### Screenshots

Open the inspector with the magnifier-list icon on a search result. The value used in the game is highlighted in green.
Here SteamGridDB wins the title, release, cover and header, while Steam supplies the rest.

![Inspecting a search result](assets/screenshots/inspect-metadata-sources/overview.png)

Hover a thumbnail for a bigger preview.

![Thumbnail preview](assets/screenshots/inspect-metadata-sources/thumbnail-preview.png)

**Only show differences** hides the rows where the sources agree. Images always count as different, as their URLs
differ by nature.

![Only show differences](assets/screenshots/inspect-metadata-sources/only-differences.png)

An edition with a base game. The base game's source (here 0 used) only fills fields the edition leaves empty. Values
that differ from the used one, such as the base game's title and release year, are marked with a warning icon.

![Inspecting an edition with a base game](assets/screenshots/inspect-metadata-sources/edition-with-base-game.png)

The plugin details show how many fields of your library each metadata plugin currently supplies.

![SteamGridDB usage](assets/screenshots/inspect-metadata-sources/plugin-usage-steamgriddb.png)

![Steam usage](assets/screenshots/inspect-metadata-sources/plugin-usage-steam.png)

### Pin metadata fields to a source

This branch builds on the inspector and makes it interactive. When plugin priority picks the worse value for a field,
you can **pin** that field to the source you prefer instead.

* In the inspector, click the pin next to a value to use that source for the field. Click the pin on a pinned value, or
  pin the source that wins anyway, to remove the pin. **Reset pinned fields** removes all of them.
* **In the match dialog:** pins are applied when you pick that result (the row shows a green pin). They belong to that
  exact selection, so picking a different result or changing the combined/base game selection starts without pins.
* **On an existing game:** the pin icon next to the edit and search buttons in the game view opens the inspector for the
  game's stored sources. **Apply** saves the pins and refreshes the game right away.
* A pin follows the *source*, not a frozen value: a metadata refresh keeps using the pinned source and picks up its
  updated data. If the pinned source stops providing the field, plugin priority decides again.
* Fields you edited by hand are never overwritten by a pin. A new manual match replaces the pins of the game with the
  ones you set in that match.
* Admin-only endpoints: `GameEndpoint.getMetadataLinks`, `GameEndpoint.setFieldOverrides`, and an optional
  `fieldOverrides` argument on `matchManually`. Pins are stored in a new `game_field_override` table (Liquibase
  changeset `2.6.0-game-field-override`).

### Editions, versions, and included DLC

Gameyfin can track the store version of your games, the edition you own, and the DLC included with your copy. Link
store products to a game in its metadata editor, label editions such as Deluxe, and compare the latest store version
with the version in your library. Gameyfin can suggest DLC from names in your game library, and you can adjust the
selection or add extras the store does not list. Game details show the edition, version, and included extras.

#### Game Store Provider plugins

Stores are provided by plugins in the new **Game Store Provider** category, listed between the metadata and download
provider categories. There is one plugin per data source:

| Plugin | Store | Provides |
|---|---|---|
| `plugins/steamstore` (Steam Store) | Steam | Search, DLC |
| `plugins/steamcmd` (Steam Builds, unofficial) | Steam | Versions (branch builds) |
| `plugins/gog` (GOG Store) | GOG | Search, versions, DLC |
| `plugins/gogdb` (GOG Builds, unofficial) | GOG | Versions, edition names |
| `plugins/epic` (Epic Games Store, egdata.app) | Epic | Search, versions, DLC |
| `plugins/itch` (itch.io, public pages) | itch.io | Search, versions (undated upload names, no DLC or editions) |

![Game Store Provider plugins](assets/screenshots/editions-dlc/plugins-game-store-provider.png)

- **Several sources per store:** search and versions use the highest-priority source that has a result and fall back
  to the others. DLC and edition names from all sources are merged without duplicates.
- **Order:** the store dropdown follows the plugin order set in the plugin admin menu, so you can put your most used
  store first.
- **Search is optional:** plugins that cannot search (SteamCMD, GOGDB) only add data. If no enabled plugin of a store
  can search, you can still add products by ID or URL.
- **No store plugin enabled:** the **Store versions** section is hidden and the refresh task does nothing.
- **Plugin disabled:** its links stay visible read-only with cached data and are not refreshed.

#### Writing a store plugin

Store plugins are separate from metadata plugins: a plugin that provides metadata does not provide store data. A
plugin that wants to add a store ships as its own plugin implementing the `GameStoreProvider` extension point of the
plugin API. It becomes another source for its store and is combined with the others as described above. Only implement what
your source provides: `search`, `fetchVersions`, `fetchDlc` and `suggestEditions` all have empty defaults, and a
plugin that cannot search should set `supportsSearch = false`.

The Steam builds, GOGDB, Epic and itch.io plugins use unofficial services or scrape public pages. `.github/workflows/store-plugins-live-check.yml`
runs a small live test for them every week. To run it locally, set `GAMEYFIN_LIVE_TESTS=true` and run the tests of
`:plugins:steamcmd`, `:plugins:gogdb`, `:plugins:epic` and `:plugins:itch`.

#### Screenshots

Open **Store versions** in the game metadata editor and choose **Add store**, then pick one of the enabled stores and
search for the game.

![Store versions section with no store linked](assets/screenshots/editions-dlc/store-versions-empty.png)

![Choosing a store](assets/screenshots/editions-dlc/store-picker.png)

A linked store has an edition and the product version to track.

![Store link with edition and version](assets/screenshots/editions-dlc/store-link-editor.png)

Each link also has an **Extras** section for DLC, edition upgrades, and bonus material that come with your copy. Use
**Detect extras** to suggest them from your files, or add extras the store does not list.

![Managing extras for a store link](assets/screenshots/editions-dlc/extras-editor.png)

Game details show the store version with an update status, plus the selected extras.

![Steam version and extras in game details](assets/screenshots/editions-dlc/details-steam-extras.png)

![Steam update available in game details](assets/screenshots/editions-dlc/details-steam-update.png)

![GOG version in game details](assets/screenshots/editions-dlc/details-gog-version.png)

### Documentation

The documentation and screenshots are available at [gameyfin.org](https://gameyfin.org/).

### Features

✨ Automatically scans and indexes your game libraries  
⬇️ Access your library via your web browser & download games directly from there  
👥 Share your library with friends & family  
⚛️ LAN-friendly (everything is cached locally - except for videos)  
🐋 Runs in a container or any system with a JVM  
🌈 Themes (including colorblind support)  
🔌 Easily expandable with plugins  
🔒 Integrates into your SSO solution via OAuth2 / OpenID Connect  
🆓 **100% open source and free to use without any paywall.**

### Feature branches

These features are also available as standalone branches, each with its own README and screenshots:

* [Store links and version tracking](https://github.com/Shade1982/gameyfin/blob/feature/version-management/README.md) (`feature/version-management`)
* [Editions, versions, and included DLC](https://github.com/Shade1982/gameyfin/blob/feature/editions-dlc/README.md) (`feature/editions-dlc`)
* [Server announcements](https://github.com/Shade1982/gameyfin/blob/feature/announcements/README.md) (`feature/announcements`)
* [Improved metadata search](https://github.com/Shade1982/gameyfin/blob/feature/improved-metadata-search/README.md) (`feature/improved-metadata-search`)
* [Inspect metadata sources](https://github.com/Shade1982/gameyfin/blob/feature/inspect-metadata-sources/README.md) (`feature/inspect-metadata-sources`)
* [Pin metadata fields to a source](https://github.com/Shade1982/gameyfin/blob/feature/metadata-field-overrides/README.md) (`feature/metadata-field-overrides`)

### Contribute to Gameyfin

Contributions are welcome!  
There are no strict requirements to contribute, but please contact us first if you want to implement a new feature or
change the design of the application before you start working on it.

### Technical Details

Gameyfin v2 is written in Kotlin and uses the following libraries/frameworks:

* Spring Boot 3 for the backend
* Vaadin Hilla & React for the frontend
* PF4J for the plugin system
* H2 database for persistence

### Acknowledgements

[![YourKit Logo](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/)  
Gameyfin is supported by [YourKit](https://www.yourkit.com/), the makers
of [YourKit Java Profiler](https://yourkit.com/java/profiler/), a powerful tool for profiling Java and Kotlin
applications.
