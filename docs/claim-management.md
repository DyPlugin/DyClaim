# Claim management

## Joined borders

`/claim see` keeps the existing time, distance and particle limits. Within a local search of at most eight chunks in each direction, claims connected by a full side and belonging to the same primary owner are outlined together. Internal edges disappear. Diagonally touching claims, other owners and other worlds are not merged. Wilderness still shows the current chunk.

This is a nearby outline, not a whole-world map. Large estates can have their outer border beyond the display distance. Clipping does not invent an ownership boundary. Java particles rotate through the outline within the existing per-run budget. Bedrock block borders require Floodgate, use at most 256 temporary positions, do not load terrain and are restored when the view closes. On Folia, terrain reads/restoration run on the relevant region and packets on the player owner.

## List filters

The original `/claim list` and `/claim list 2` remain valid. Filters can be combined, with an optional page before them:

```text
/claim list name home
/claim list world world_nether
/claim list sale listed
/claim list 2 world world sale unlisted

/claim liste isim ev
/claim liste dünya world_nether satış satışta
```

`name` is a case-insensitive substring match and accepts Turkish/ASCII spelling. `world` matches the full stored world name, case-insensitively. `sale` accepts `all`, `listed` or `unlisted` (Turkish: `tümü`, `satışta`, `satışta-değil`). Filter words accept both languages; values such as world names are not translated. A filter value occupies one command argument; use a distinctive word when searching a name containing spaces.

Numbers always match the unfiltered `/claim tp <number>` list. A filtered list may therefore show rows 2, 7 and 12 instead of renumbering them. Filtering changes presentation only and never changes ownership, data or teleport destinations.

## Personal access

`/claim info` adds the viewer's role and effective building, container, door, trapdoor, redstone, entity and teleport rights. Effective rights use the same access policy as protection, including bypass, clan grants and public doors. A currently active temporary player trust grant shows its remaining duration. This does not reveal other players' trust entries to visitors.

## Administrator diagnostics

`/claim admin diagnose` requires `dyclaim.admin` and supports the server console. Turkish aliases: `tanı` and `tani`. Help and TAB follow the effective language and permission.

The short read-only summary shows plugin version, distribution, writable claim storage/count, economy availability and enabled optional plugin names. It performs no payment, data rewrite, remote upload or provider test. An installed integration can still be incompatible or fail its hook; this command does not certify it. No filesystem paths, credentials or raw provider exceptions are displayed.
