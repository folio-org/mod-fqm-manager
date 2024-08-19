import {
  BoxAndWiskers,
  BoxPlotController,
} from '@sgratzl/chartjs-chart-boxplot';
import {
  CategoryScale,
  Chart,
  ChartConfiguration,
  LinearScale,
} from 'chart.js';
import { randomUUID } from 'crypto';
import { Stats } from 'fast-stats';
import { readFileSync } from 'fs';
import json5 from 'json5';

Chart.register(BoxPlotController, BoxAndWiskers, LinearScale, CategoryScale);

const COLORS = ['#f007', '#0f07', '#00f7'];

const keys = Bun.argv.slice(2);
const results: Record<string, Record<string, number[]>> = {};
const descriptions: Record<string, Record<string, string>> = {};
keys
  .map((f) => [f, readFileSync(`raw-results/${f}.json`, 'utf8')] as const)
  .map(([f, s]) => [f, JSON.parse(s) as Record<string, number[]>] as const)
  .forEach(([f, r]) => (results[f] = r));
keys
  .map(
    (f) =>
      [f, readFileSync(`raw-results/${f}-descriptions.json`, 'utf8')] as const
  )
  .map(([f, s]) => [f, JSON.parse(s) as Record<string, string>] as const)
  .forEach(([f, r]) => (descriptions[f] = r));

const metrics = [
  ...new Set<string>([
    ...Object.values(results).flatMap((o) => Object.keys(o)),
  ]),
].toSorted((a, b) => a.localeCompare(b));

const queries: Record<
  string,
  {
    entityType: string[];
    queries: { label: string; queries: unknown[] }[];
    fields: { label: string; fields: string[][] }[];
  }
> = await json5.parse(await Bun.file('queries.json5').text());

function parseMetricLabel(label: string) {
  const [entityType, query, fields, category] = label.split('|');
  return { entityType, query, fields, category };
}
function getDescriptionMetricKey(label: string) {
  const parsed = parseMetricLabel(label);
  return `${parsed.entityType}|${parsed.query}|${parsed.fields}`;
}

function renderChart(options: ChartConfiguration, rows = keys.length): string {
  const id = randomUUID();
  return `
  <div style="height: ${rows * 50}px; width: 800px; position: relative;">
    <canvas id="${id}"></canvas>
  </div>
  <script>
    new Chart(document.getElementById("${id}").getContext("2d"), ${JSON.stringify(
    {
      ...options,
      options: { ...options.options, aspectRatio: 800 / (rows * 50) },
    }
  )});
  </script>`;
}

async function getSummaryTable() {
  const summaryStats: Record<
    string,
    {
      min: Stats;
      median: Stats;
      max: Stats;
      mean: Stats;
      stddev: Stats;
    }
  > = keys.reduce((acc, key) => {
    return {
      ...acc,
      [key]: {
        min: new Stats(),
        median: new Stats(),
        max: new Stats(),
        mean: new Stats(),
        stddev: new Stats(),
      },
    };
  }, {});
  return `
  <table>
    <thead>
      <tr>
        <th scope="col">Test case</th>
        <th scope="col">Version</th>
        <th scope="col" class="nerd"># samples</th>
        <th scope="col" class="nerd">min</th>
        <th scope="col">median</th>
        <th scope="col" class="nerd">max</th>
        <th scope="col">mean</th>
        <th scope="col" class="nerd">stddev</th>
        <th scope="col">MOE</th>
        <th scope="col">∆ (median)</th>
      </tr>
    </thead>
    <tbody>
      ${metrics
        .map(
          (m, metricIndex) => `
        <tr style="${
          metricIndex % 2 === 0 ? '' : 'background-color: #eee;'
        }" data-metric="${m}">
          <th scope="row" rowspan="${
            keys.length
          }"><a href="#${m}">${m}</a><br />${[
            ...new Set(
              Object.values(descriptions).map(
                (v) => v[getDescriptionMetricKey(m)]
              )
            ),
          ].join('<br />')}</th>
          ${keys
            .map((k, keyIndex) => {
              if (!results[k][m]) {
                return `
                  <th scope="row" style="font-family: monospace">${k}</th>
                  <td class="nerd">-</td>
                  <td class="nerd">-</td>
                  <td>-</td>
                  <td class="nerd">-</td>
                  <td>-</td>
                  <td class="nerd">-</td>
                  <td>-</td>
                  <td>-</td>
                `;
              }
              const stats = new Stats().push(
                ...results[k][m].map((x) => x / 1000)
              );
              const prevStats =
                keyIndex === 0 || !results[keys[keyIndex - 1]][m]
                  ? null
                  : new Stats().push(
                      ...results[keys[keyIndex - 1]][m].map((x) => x / 1000)
                    );

              if (keys.every((k) => m in results[k])) {
                summaryStats[k].min.push(stats.min!);
                summaryStats[k].median.push(stats.percentile(50));
                summaryStats[k].max.push(stats.max!);
                summaryStats[k].mean.push(stats.amean());
                summaryStats[k].stddev.push(stats.stddev());
              }

              return `
                <th scope="row" style="font-family: monospace">${k}</th>
                <td class="nerd">${stats.length}</td>
                <td class="nerd">${stats.min!.toFixed(3)}</td>
                <td>${stats.percentile(50).toFixed(3)}</td>
                <td class="nerd">${stats.max!.toFixed(3)}</td>
                <td>${stats.amean().toFixed(3)}</td>
                <td class="nerd">${stats.stddev().toFixed(3)}</td>
                <td>±${stats.moe().toFixed(3)}</td>
                ${
                  prevStats
                    ? purdyPercent(
                        (stats.percentile(50) / prevStats.percentile(50) - 1) *
                          100
                      )
                    : '<td>-</td>'
                }
              `;
            })
            .join(
              `</tr><tr style="${
                metricIndex % 2 === 0 ? '' : 'background-color: #eee;'
              }" data-metric="${m}">`
            )}
        </tr>`
        )
        .join('')}
    </tbody>
    <tfoot>
      ${keys
        .map((k, i) => {
          const stats = summaryStats[k];
          const prevStats = i === 0 ? null : summaryStats[keys[i - 1]];
          return `
        <tr>
          <th scope="row" colspan="2" style="font-family: monospace; font-weight: bold;">${k}</th>
          <td class="nerd" />
          ${
            prevStats
              ? `${purdyPercent(
                  ((stats.min.sum - prevStats.min.sum) / prevStats.min.sum) *
                    100,
                  stats.min.sum.toFixed(3) + '<br/>',
                  true
                )}${purdyPercent(
                  ((stats.median.sum - prevStats.median.sum) /
                    prevStats.median.sum) *
                    100,
                  stats.median.sum.toFixed(3) + '<br/>'
                )}${purdyPercent(
                  ((stats.max.sum - prevStats.max.sum) / prevStats.max.sum) *
                    100,
                  stats.max.sum.toFixed(3) + '<br/>',
                  true
                )}${purdyPercent(
                  ((stats.mean.sum - prevStats.mean.sum) / prevStats.mean.sum) *
                    100,
                  stats.mean.sum.toFixed(3) + '<br/>'
                )}${purdyPercent(
                  ((stats.stddev.sum - prevStats.stddev.sum) /
                    prevStats.stddev.sum) *
                    100,
                  stats.stddev.sum.toFixed(3) + '<br/>',
                  true
                )}<td>-</td><td>-</td>`
              : `
                  <td class="nerd">${stats.min.sum.toFixed(3)}</td>
                  <td>${stats.median.sum.toFixed(3)}</td>
                  <td class="nerd">${stats.max.sum.toFixed(3)}</td>
                  <td>${stats.mean.sum.toFixed(3)}</td>
                  <td class="nerd">${stats.stddev.sum.toFixed(3)}</td>
                  <td>-</td>
                  <td>-</td>
                `
          }
        </tr>`;
        })
        .join('')}
    </tfoot>
  </table>`;
}

function getMetricInfo(metric: string) {
  return `
  <section data-metric="${metric}">
    <h2 id="${metric}">${metric.replaceAll('|', ' • ')}</h2>
    <details>
      <summary>Query</summary>
      <pre>
      ${JSON.stringify(
        {
          entityType: queries[parseMetricLabel(metric).entityType].entityType,
          query: queries[parseMetricLabel(metric).entityType].queries.find(
            (q) => q.label === parseMetricLabel(metric).query
          )?.queries,
          fields: queries[parseMetricLabel(metric).entityType].fields.find(
            (f) => f.label === parseMetricLabel(metric).fields
          )?.fields,
        },
        null,
        2
      )}
      </pre>
    </details>
    <table>
      <thead>
        <tr>
          <th scope="col">Version</th>
          <th scope="col">Result</th>
          <th scope="col" class="nerd"># samples</th>
          <th scope="col" class="nerd">min</th>
          <th scope="col" class="nerd">Q1</th>
          <th scope="col">median</th>
          <th scope="col" class="nerd">Q3</th>
          <th scope="col" class="nerd">max</th>
          <th scope="col" class="nerd">range</th>
          <th scope="col" class="nerd">IQR mean</th>
          <th scope="col" class="nerd">mean</th>
          <th scope="col" class="nerd">stddev</th>
          <th scope="col" class="nerd">MOE</th>
          <th scope="col">95% CI min mean</th>
          <th scope="col">95% CI max mean</th>
        </tr>
      </thead>
      <tbody>
        ${keys
          .filter((k) => metric in results[k])
          .map((k) => {
            const stats = new Stats().push(
              results[k][metric].map((x) => x / 1000)
            );
            return `
              <tr>
                <th scope="row" style="font-family: monospace">${k}</th>
                <td>${descriptions[k][getDescriptionMetricKey(metric)]}</td>
                <td class="nerd">${stats.length}</td>
                <td class="nerd">${stats.min!.toFixed(3)}</td>
                <td class="nerd">${stats.percentile(25).toFixed(3)}</td>
                <td>${stats.percentile(50).toFixed(3)}</td>
                <td class="nerd">${stats.percentile(75).toFixed(3)}</td>
                <td class="nerd">${stats.max!.toFixed(3)}</td>
                <td class="nerd">${Math.abs(
                  stats.range()[1] - stats.range()[0]
                ).toFixed(3)}</td>
                <td class="nerd">${stats.iqr().amean().toFixed(3)}</td>
                <td class="nerd">${stats.amean().toFixed(3)}</td>
                <td class="nerd">${stats.stddev().toFixed(3)}</td>
                <td class="nerd">±${stats.moe().toFixed(3)}</td>
                <td>${(stats.amean() - stats.moe()).toFixed(3)}</td>
                <td>${(stats.amean() + stats.moe()).toFixed(3)}</td>
              </tr>`;
          })
          .join('')}
      </tbody>
    </table>

    ${renderChart({
      type: 'boxplot',
      data: {
        labels: [metric],
        datasets: keys
          .filter((k) => metric in results[k])
          .map((k, i) => ({
            label: k,
            backgroundColor: COLORS[i],
            borderColor: 'black',
            borderWidth: 1,
            outlierColor: 'black',
            outlierBackgroundColor: 'black',
            padding: 10,
            itemRadius: 0,
            meanBackgroundColor: 'black',
            data: [results[k][metric].map((x) => x / 1000)],
          })),
      },
      options: {
        responsive: false,
        maintainAspectRatio: false,
        indexAxis: 'y',
        scales: {
          x: {
            beginAtZero: false,
          },
          y: {
            beginAtZero: false,
          },
        },
      },
    })}

    <hr />
  </section>`;
}

const result = `
<!DOCTYPE html>
<html>
  <head>
    <style>

    thead,
    tfoot {
      background-color: #ccc;
    }

    table {
      border-collapse: collapse;
      border: 4px double black;
      font-family: sans-serif;
      font-size: 0.8rem;
      letter-spacing: 1px;
    }

    caption {
      caption-side: bottom;
      padding: 10px;
    }

    th,
    td {
      border: 1px solid black;
      padding: 8px 10px;
    }

    th {
      text-align: left;
    }
    td {
      text-align: right;
      font-family: monospace;
    }

    body, section {
      display: flex;
      flex-direction: column;
      align-items: center;
    }

    hr, section {
      width: 100%;
    }

    canvas {
      width: 100%;
      height: 100%;
    }

    body:not(.show-nerd-cols) .nerd {
      display: none;
    }

    body:not(.show-all) [data-metric$="|all"],
    body:not(.show-query) [data-metric$="|query"],
    body:not(.show-import-results) [data-metric$="|import-results"] {
      display: none;
    }
    </style>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <script src="https://unpkg.com/@sgratzl/chartjs-chart-boxplot"></script>
  </head>
  <body class="show-query">
    <h1>Results</h1>
    <h4>All datapoints are in seconds unless otherwise specified</h4>

    <label for="toggle-nerd"><input type="checkbox" id="toggle-nerd" /> Show nerd-only columns</label>
    <div>
      <label for="show-all"><input type="checkbox" id="show-all" /> Show total refresh times</label>
      <label for="show-query"><input type="checkbox" id="show-query" checked="checked" /> Show query-only times</label>
      <label for="show-import-results"><input type="checkbox" id="show-import-results" /> Show import-only times</label>
    </div>

    <script>
      document.getElementById('toggle-nerd').addEventListener('change', (e) => {
        document.body.classList.toggle('show-nerd-cols', e.target.checked);
      });
      document.getElementById('show-all').addEventListener('change', (e) => {
        document.body.classList.toggle('show-all', e.target.checked);
      });
      document.getElementById('show-query').addEventListener('change', (e) => {
        document.body.classList.toggle('show-query', e.target.checked);
      });
      document.getElementById('show-import-results').addEventListener('change', (e) => {
        document.body.classList.toggle('show-import-results', e.target.checked);
      });
    </script>
    <hr />

    ${await getSummaryTable()}
    <hr />


    ${metrics
      .filter((m) => m.includes('|'))
      .map(getMetricInfo)
      .join('')}
  </body>
</html>
`.trim();

await Bun.write('results.html', result);
console.log('Done!');

function purdyPercent(
  percent: number,
  src: string = '',
  isNerd: boolean = false
) {
  const val =
    percent >= 0
      ? `${src}+${percent.toFixed(2)}%`
      : `${src}${percent.toFixed(2)}%`;
  const clazz = isNerd ? 'nerd' : '';
  const template = (color: string) =>
    `<td style="background-color: ${color}" class="${clazz}">${val}</td>`;

  if (percent > 25) {
    return template('#E57373; font-weight: bold;');
  } else if (percent > 10) {
    return template('#EF9A9A');
  } else if (percent > 5) {
    return template('#FFCDD2');
  } else if (percent > 2) {
    return template('#FFEBEE');
  } else if (percent >= 0) {
    return template('#FFFFFF');
  } else if (percent >= -2) {
    return template('#FFFFFF');
  } else if (percent > -5) {
    return template('#E8F5E9');
  } else if (percent > -10) {
    return template('#C8E6C9');
  } else if (percent > -15) {
    return template('#A5D6A7');
  } else if (percent > -20) {
    return template('#81C784');
  } else if (percent > -25) {
    return template('#66BB6A');
  } else {
    return template('#4CAF50');
  }
}
