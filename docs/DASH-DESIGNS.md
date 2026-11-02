# Bike dash designs: how they work and how to add one

The rider picks a bike screen in **Dash screen design** (`DashDesignActivity`, Setup → Screen & display, or Map settings). Custom designs only show while the app's own map runs on the bike (`GpxActivity` → `MainActivity.beginGpxProjection` → `VideoPipeline.setupGpxPresentation`). Android Auto is a separate path, so the designs do not apply there. The catalogue is `mockups/dash-designs.html` and the picker frame is in `mockups/app-redesign.html`.

## Pieces

| File | Role |
|---|---|
| `DashDesign.kt` | `DashDesign` enum (id, number, label, map rect, slots, extras, renderer factory), the `DashRenderer` interface, `DashSamples.preview` (picker sample values) and `DashFmt` (number formatting). |
| `DashDesignView.kt` | `DashDesignView` (scaling, 4 Hz tick, battery receiver, redraw on change, preview mode) and `DashScreen.inflate(ctx, design)`, which builds the bike screen. |
| `DashKit.kt` | Shared paints, icons (turn, GPS, battery), the dark card and the corner status pill. |
| `DashExtra.kt` | `DashExtra` enum, `DashPrefs` (prefs file `ocm_dash`) and `DashExtraText` (labels, units, values, signature). |
| `DashTelemetry.kt` | `DashValues` holds every displayable value. `DashTelemetry` is the live instance, refreshed by the LIVE view's tick. |
| `Stock368Renderer.kt`, `FullMapRenderer.kt`, `ArrowMapRenderer.kt` | Designs #1–#3. |
| `SpeedOnlyRenderer.kt`, `TripComputerRenderer.kt`, `SportRenderer.kt`, `NightRenderer.kt`, `TouringRenderer.kt`, `VintageRenderer.kt`, `InfoListRenderer.kt` | Designs #4–#10 (no map). |
| `DashSlots.kt` | Helpers for #4–#10: themed number + unit drawing, status icons, speed segments. |

Prefs: `opencfmoto_map/dash_layout` (AUTO / STANDARD / STOCK_368, by name, unchanged) and `opencfmoto_map/dash_design` (the `DashDesign.id`, default `stock_368`). `STOCK_368` is shown as "Custom" and means "use `dash_design`". AUTO uses it only on a Zontes 368 with a panel of at least 2.2:1. Picking a design switches STANDARD to STOCK_368 and leaves AUTO alone. Never store `ordinal`, and never rename an id.

## Add a design (#4–#20)

1. **Enum entry** in `DashDesign`:
   - Fill in the existing entry (#4–#10), or add #11–#20 using the ids in `DashDesign.RESERVED_IDS`.
   - Set the `mapRect` (null = no map), `slots`, `supportedExtras` (in the order you draw them) and `defaultExtras` (exactly what the mockup shows).
   - Set the factory: `{ c, e -> MyRenderer(c, e) }`.
2. **Renderer class** `class MyRenderer(ctx: Context, extras: Array<DashExtra>) : DashRenderer`:
   - Build the paints, paths and strings in the constructor. A `DashKit(ctx, extras)` gives you most of them.
   - Rebuild scaled paths in `onScale`.
   - Draw everything in `draw`. Map designs put the cards over the map in `drawOver`.
   - `signature`/`overSignature` hash exactly what each layer shows, at the precision shown. The view repaints only when the hash changes. Use `kit.text.signature(extras, t, is24h)` for the extras.
3. **Strings**: add `dash_design_N` (already there for 1–10) and any labels to both `values/strings.xml` and `values-th/strings.xml`.
4. **Preview**: there is nothing extra to do. The picker binds a `DashDesignView` in `Mode.PREVIEW` to `DashSamples.preview()`. If you need a new value, add it to `DashValues` and give it a sample there.
5. Build, then check the picker card, the options sheet, and the bike at 1024×425.

A design with `factory = null` shows as "coming soon" in the picker. If a pref points at one, `DashScreen` falls back to #1.

## Coordinates and scale

- Draw in **1024×425 design px**, the mockup's canvas.
- In `draw(c, t, sx, sy)`, `sx = width/1024` and `sy = height/425`. Scale x by `sx` and y by `sy`. Scale text, icons, radii and padding by `s = min(sx, sy)`.
- Icons are unit-space paths (24 or 40 units) drawn under `c.save(); translate; scale(size/24f)`.
- Map designs: the map slot is `mapRect` (design px), with corners rounded by `mapRadius`. `DashScreen` lays the map out there.
  - `draw` paints the layer under the map.
  - `drawOver` runs on a full-size, non-clickable overlay view in the same coordinates, so touches reach the map. Translate to the map origin yourself if needed (see #1).
- Non-map designs: `presentation_gpx` stays bound so GPS, route and turn cues keep feeding `DashTelemetry`, but it is INVISIBLE, which means no drawing and no touches. `GpxDashUi(mapVisible = false)` never resumes the map engine. Handlebar events still go to `DashScreen.root`.

## Drawing rules

- **No allocation** in `draw`, `drawOver` or the signatures:
  - Paints, Paths and RectF are fields.
  - Text goes through one reused `StringBuilder` (`buf.setLength(0)`, then `append`, then `drawText(buf, 0, buf.length, …)`).
  - No `String.format`, string templates or `toString()`.
  - Use `kit.drawFitted` to cut long text.
- **Never fake data.** Unknown phone values (NaN / -1) show "--".
- Speed is `t.speedKmh`, which the fresh-fix gate in `DashTelemetry.refresh` already sets to NaN after 5 s without a fix. Do not read GPS yourself.
- The tick is 4 Hz, owned by the LIVE view. Do not add timers or receivers in a renderer.
- Colours follow the mockup and must keep a contrast of at least 4.5:1.

## Extras (per-design options)

`DashExtra` values: `TRIP_KM, RIDE_TIME, MAX_SPEED, AVG_SPEED, CLOCK, ALTITUDE, HEADING, PHONE_BATTERY, ODOMETER, FUEL_ECONOMY, KM_SINCE_REFUEL, RANGE_EST, TYRES, COOLANT`. The ids are stored in prefs, so never change them.

- Storage is prefs file `ocm_dash`:
  - `extras_<designId>` is a string set. If it is absent, the design's `defaultExtras` apply.
  - `tank_liters` is a float. If it is absent, there is no range estimate.
- `DashPrefs.extras(ctx, design)` returns the chosen extras in the design's order, capped at `slots`. `DashScreen` resolves this list **once** at inflate and passes it to the renderer, so there are no set lookups while drawing.
- In the options sheet, tapping a selected card (or its "ตัวเลือก" button) shows:
  - a live preview at the top;
  - one toggle per supported extra;
  - an "เลือกแล้ว n จาก slots" counter;
  - the tank-size field when `RANGE_EST` is supported.
- `optional` extras (fuel, tyres, coolant) are **hidden when unknown** and leave no gap: renderers skip them and close up the space (#1 lets the remaining panels share the column). The other extras show "--".
- Sources, all computed in `DashTelemetry.refresh`:
  - **AVG_SPEED** = trip km ÷ moving time. It is NaN for the first minute.
  - **FUEL_ECONOMY** = 100 ÷ `Maintenance.consumption(book)` L/100 km, shown as km/L. It needs at least 2 full refuels.
  - **KM_SINCE_REFUEL** = `book.appKm` + the live trip − the km of the last `fuel` entry.
  - **RANGE_EST** = tank × km/L − km since refuel. It needs `tank_liters`.
  - The fuel figures are recomputed only when the book changes (at bind, when a trip is saved, or when a refuel is logged). The book is re-read on the existing 30 s cadence, never per frame.
- Each renderer chooses the slot style: #1 uses left panels, #2 and #3 use the corner status pill (`DashKit.cornerPill`). For tiles or list rows, loop over `extras` with an index, skip `!text.visible(e, t)`, and draw `text.label(e)`, `text.value(buf, e, t, is24h)` and `text.unit(e)`.

## Vehicle sensors (tyres, coolant)

`DashValues.tyreFrontBar`, `tyreRearBar`, `tyreFrontC`, `tyreRearC` and `coolantC` are `Float?` and **stay null**: the phone has no source today (see `docs/RE-VEHICLE-TELEMETRY.md`). A future BLE TPMS sensor or OBD adapter will write them on the main thread.

- While these values are null, renderers hide the element entirely and do not draw "--".
- `TYRES` and `COOLANT` show dimmed with a "ต้องมีเซนเซอร์" hint in the options sheet. Riders can still pre-enable them so they appear by themselves once a sensor exists.
- Tyre temperatures (`tyre*C`) are not drawn by any design yet.
- Do not add Bluetooth or OBD code as part of a design.
