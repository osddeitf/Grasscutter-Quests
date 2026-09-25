# SurrealDB backend

Grasscutter's persistence layer is pluggable. **MongoDB (via Morphia) is the default**, but the
server can also run against **SurrealDB** — including an **embedded, in-process** database, so you
can run with no separate database server at all.

The backend is chosen per datastore in `config.json`; MongoDB behaviour is unchanged unless you opt
in.

## Configuration

Under `database.game` (and `database.server` when running in `GAME_ONLY` mode):

```jsonc
{
  "database": {
    "game": {
      "backend": "surreal",                 // "mongodb" (default) or "surreal"
      "connectionUri": "surrealkv://./database",
      "collection": "grasscutter"            // used as the SurrealDB database name
    }
  }
}
```

### `connectionUri` values

| URI | Meaning |
|-----|---------|
| `surrealkv://./database` | **Embedded**, persisted to the `./database` folder (survives restarts). Recommended for serverless setups. |
| `memory` | **Embedded**, in-memory only. Everything is lost on shutdown — handy for testing. |
| `ws://host:8000` / `http://host:8000` | Connect to a **remote/standalone** SurrealDB server. |

The namespace is fixed to `grasscutter`; the database name is taken from `collection`.

## Running the packaged jar (required: `surrealdb.jar`)

The SurrealDB SDK bundles native binaries for every platform (~300 MB), so **it is deliberately not
included in the built jar** (this keeps builds fast and the distribution small). When you run the
**packaged jar** on the SurrealDB backend, place the SDK jar next to `grasscutter.jar`, named
`surrealdb.jar`:

```sh
# Obtain the SDK jar (any 2.x release — see "Versioning" below) and rename it:
cp surrealdb-2.1.2.jar  <server-folder>/surrealdb.jar

# Then run as usual:
java -jar grasscutter.jar
```

It is wired in through the jar's manifest `Class-Path`, and is **loaded lazily**: the SDK classes are
only loaded when the SurrealDB backend is actually used. Consequences:

- Running with `backend=mongodb` and **no** `surrealdb.jar` present works fine — the SDK is never touched.
- Running with `backend=surreal` but **without** `surrealdb.jar` fails with a `NoClassDefFoundError`
  for `com.surrealdb.*`; add the jar to fix it.

You can download the SDK jar from Maven Central (`com.surrealdb:surrealdb`) or copy it from your
Gradle cache after a build:

```
~/.gradle/caches/modules-2/files-2.1/com.surrealdb/surrealdb/2.1.2/*/surrealdb-2.1.2.jar
```

## Development (IntelliJ IDEA / `gradlew run`)

No extra steps. The SDK is a normal (`implementation`) dependency, so it is on the runtime classpath
when running or debugging from the IDE or via `gradlew run`. You can set `backend=surreal` and debug
directly — the external `surrealdb.jar` is only needed for the standalone packaged jar.

## Migrating existing MongoDB data

To move an existing MongoDB database into SurrealDB, start the server with the **normal
`backend=mongodb` config** plus the migration flag:

```sh
# surrealdb.jar must be present next to the server jar (see above).
java -jar grasscutter.jar -migrate-surreal=surrealkv://./database
```

This copies every game collection from MongoDB into the target SurrealDB database and then exits,
logging a per-collection count:

```
Starting MongoDB -> SurrealDB migration into surrealkv://./database (db=grasscutter)
  DatabaseCounter: 2 migrated, 0 failed
  Account: 18 migrated, 0 failed
  ...
Migration complete: N records migrated, M failed.
```

Notes:
- The target database name is taken from `database.game.collection`, so a subsequent run with
  `backend=surreal` and the same `collection` reads the migrated data.
- A failure in one record/collection is logged and skipped; it does not abort the whole migration.
- Assumes `HYBRID` run mode (accounts share the game datastore).

After a clean migration, set `database.game.backend` to `surreal` and start the server normally.

## How it works (for developers)

- `GameRepository` is the backend-agnostic seam. `MorphiaGameRepository` (MongoDB) and
  `SurrealGameRepository` (SurrealDB) implement it; `DatabaseManager` selects one from `backend`.
- Entities keep their existing Morphia annotations as the single source of mapping truth.
  `DocumentMapper` (Gson) serialises them to a plain document that the SurrealDB SDK stores via
  `upsert(recordId, CONTENT, map)`; reads walk the returned value back into an entity. The SDK's
  typed POJO binder is not used (it cannot instantiate Java enums).
- Record identity comes from each entity's `@Id`. `ObjectId`-keyed entities get a freshly generated
  `ObjectId` for new records (its hex becomes the record id), so **existing MongoDB `_id`s are never
  altered** and Mongo data stays readable.
- Morphia lifecycle hooks (`@PrePersist`/`@PreLoad`/`@PostLoad`) are invoked reflectively on the
  SurrealDB path, since the SDK has no callbacks.

### Versioning of the SDK jar

The SDK follows semantic versioning in practice within its major line (the 2.x releases are
additive/bugfix with an unchanged public API). The project compiles against **2.1.2**, so the
runtime `surrealdb.jar` should be **≥ 2.1.2 and < 3.0**. On a major (3.x) upgrade, bump the version
in `build.gradle` and re-test.

## Current limitations

- The embedded `Surreal` connection is shared across concurrent players; heavy concurrency has not
  been hardened. Report issues if you hit contention.
