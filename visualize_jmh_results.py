import csv
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt


def load_jmh_csv(path: Path):
    with path.open(newline="") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
    return rows


def detect_param_column(rows, param_name: str) -> str:
    candidates = [param_name, f"Param:{param_name}"]
    if not rows:
        raise ValueError("No rows in CSV.")
    columns = rows[0].keys()
    for c in candidates:
        if c in columns:
            return c
    raise KeyError(
        f"Could not find parameter column for '{param_name}'. "
        f"Available columns: {', '.join(columns)}"
    )


def plot_benchmark_vs_param(rows, benchmark_filter: str, param_name: str, out_dir: Path):
    filtered = [r for r in rows if benchmark_filter in r["Benchmark"]]
    if not filtered:
        print(f"No rows matched benchmark filter '{benchmark_filter}'.")
        return

    param_col = detect_param_column(filtered, param_name)

    grouped = defaultdict(list)
    for r in filtered:
        try:
            x = float(r[param_col].replace("_", ""))
            y = float(r["Score"])
        except (ValueError, KeyError):
            continue
        key = r["Benchmark"]
        grouped[key].append((x, y, r.get("Unit", "")))

    out_dir.mkdir(parents=True, exist_ok=True)

    for bench_name, points in grouped.items():
        if not points:
            continue
        points.sort(key=lambda t: t[0])
        xs = [p[0] for p in points]
        ys = [p[1] for p in points]
        unit = points[0][2] if points[0][2] else "time"

        plt.figure()
        plt.plot(xs, ys, marker="o")
        plt.xlabel(param_name)
        plt.ylabel(f"Score ({unit})")
        plt.title(bench_name)
        plt.grid(True)

        safe_name = bench_name.replace(".", "_").replace("/", "_")
        out_path = out_dir / f"{safe_name}_vs_{param_name}.png"
        plt.savefig(out_path, bbox_inches="tight")
        plt.close()
        print(f"Wrote {out_path}")


def find_default_csv() -> Path | None:
    candidates = [
        Path("app") / "build" / "jmh-results.csv",
        Path(__file__).parent / "app" / "build" / "jmh-results.csv",
        Path("jmh-results.csv"),
    ]
    for p in candidates:
        if p.is_file():
            return p
    return None


def auto_plot_all(csv_path: Path) -> int:
    rows = load_jmh_csv(csv_path)
    if not rows:
        print(f"No rows found in CSV: {csv_path}")
        return 1

    out_dir = csv_path.parent / "jmh-plots"

    columns = list(rows[0].keys())
    param_cols = [c for c in columns if c.startswith("Param:")]

    if not param_cols:
        print("No parameter columns found in CSV.")
        return 1
    if len(param_cols) > 1:
        print(
            "Expected a single parameter column, "
            f"but found multiple: {', '.join(param_cols)}"
        )
        return 1

    param_col = param_cols[0]
    param_name = param_col.removeprefix("Param:")

    by_benchmark = defaultdict(list)
    for r in rows:
        by_benchmark[r["Benchmark"]].append(r)

    for bench_name, bench_rows in by_benchmark.items():
        values = []
        for r in bench_rows:
            v = r.get(param_col, "")
            if not v:
                continue
            try:
                values.append(float(v.replace("_", "")))
            except ValueError:
                continue

        if len(set(values)) < 2:
            continue

        plot_benchmark_vs_param(rows, bench_name, param_name, out_dir)

    return 0


def main() -> int:
    csv_path = find_default_csv()
    if csv_path is None:
        return 1

    return auto_plot_all(csv_path)


if __name__ == "__main__":
    raise SystemExit(main())

