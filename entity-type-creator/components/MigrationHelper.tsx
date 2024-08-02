import { EntityType } from '@/types';
import { java } from '@codemirror/lang-java';
import { json } from '@codemirror/lang-json';
import { Button, Container, FormControlLabel, Grid, Switch } from '@mui/material';
import CodeMirror, { EditorView } from '@uiw/react-codemirror';
import { constantCase } from 'change-case';
import json5 from 'json5';
import { useEffect, useMemo, useRef, useState } from 'react';

export default function MigrationHelper() {
  const [oldEntityType, setOldEntityType] = useState<EntityType | null>(null);
  const [newEntityType, setNewEntityType] = useState<EntityType | null>(null);
  const [columnMapping, setColumnMapping] = useState<Record<string, string>>({});

  const canvasRef = useRef<HTMLCanvasElement>();

  const [showMatched, setShowMatched] = useState(true);
  const [showOnlyMatchingDataTypes, setShowOnlyMatchingDataTypes] = useState(false);
  const [clicked, setClicked] = useState<string | null>(null);
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 });

  const result = useMemo(() => {
    let result = '';

    const oldAndNewAreSame = oldEntityType?.id === newEntityType?.id;

    const oldNameUnqualified = constantCase(oldEntityType?.name ?? 'FillMeIn');
    const oldName = oldAndNewAreSame ? oldNameUnqualified : `OLD_${oldNameUnqualified}`;
    const newNameUnqualified = constantCase(newEntityType?.name ?? 'FillMeIn');
    const newName = oldAndNewAreSame ? newNameUnqualified : `NEW_${newNameUnqualified}`;

    if (oldAndNewAreSame) {
      result += `
private static final UUID ${oldName} = UUID.fromString("${oldEntityType?.id}");
`;
    } else {
      result += `
private static final UUID ${oldName} = UUID.fromString("${oldEntityType?.id}");
private static final UUID ${newName} = UUID.fromString("${newEntityType?.id}");
`;
    }

    const columnMapName = oldNameUnqualified + '_COLUMN_MAPPING';
    result += `
private static final Map<String, String> ${columnMapName} = Map.ofEntries(
  ${Object.entries(columnMapping)
    .toSorted(([a], [b]) => a.localeCompare(b))
    .filter(([a, b]) => a !== b)
    .map(([oldName, newName]) => `Map.entry("${oldName}", "${newName}")`)
    .join(',\n  ')}
);
`.trimStart();

    if (oldAndNewAreSame) {
      result += `
@Override
protected Map<UUID, UUID> getEntityTypeChanges() {
  return Map.ofEntries();
}
`;
    } else {
      result += `
@Override
protected Map<UUID, UUID> getEntityTypeChanges() {
  return Map.ofEntries(
    Map.entry(${oldName}, ${newName})
  );
}
`;
    }

    result += `
@Override
protected Map<UUID, Map<String, String>> getFieldChanges() {
  return Map.ofEntries(
    Map.entry(${oldName}, ${columnMapName})
  );
}
`;

    return result.trimStart();
  }, [oldEntityType, newEntityType, columnMapping]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvasRef.current?.getContext('2d');
    if (!canvas || !context) {
      return;
    }

    let oldColumns = oldEntityType?.columns || [];
    let newColumns = newEntityType?.columns || [];

    if (!showMatched) {
      oldColumns = oldColumns.filter((c) => !columnMapping[c.name]);
      newColumns = newColumns.filter((c) => !Object.values(columnMapping).includes(c.name));
    }

    oldColumns = oldColumns.toSorted((a, b) => a.name.localeCompare(b.name));
    newColumns = newColumns.toSorted((a, b) => a.name.localeCompare(b.name));

    const clickedColumn = oldColumns.find((c) => c.name === clicked);
    if (showOnlyMatchingDataTypes && clickedColumn) {
      newColumns = newColumns.filter((column) => column.dataType.dataType === clickedColumn?.dataType.dataType);
    }

    const canvasWidth = canvas.getBoundingClientRect().width;
    context.canvas.width = canvasWidth;

    const canvasHeight = Math.max(oldColumns.length, newColumns.length) * 24 + 24 * 2;
    context.canvas.height = canvasHeight;
    context.canvas.style.height = canvasHeight + 'px';

    context.reset();
    context.font = '16px monospace';

    const columnWidth = canvasWidth * 0.4;

    context.textAlign = 'right';
    context.font = 'bold 16px monospace';
    context.fillText(oldEntityType?.name ?? 'Old Entity Type', columnWidth, 16, columnWidth);

    context.font = '16px monospace';
    for (let i = 0; i < oldColumns.length; i++) {
      const column = oldColumns[i];

      if (columnMapping[column.name]) {
        context.fillStyle = 'grey';
      } else {
        context.fillStyle = 'black';
      }

      context.fillText(column.name, columnWidth, (i + 2) * 24, columnWidth);
      context.strokeRect(1, (i + 2) * 24 - 16, columnWidth + 4, 24);
    }

    context.textAlign = 'left';
    context.font = 'bold 16px monospace';
    context.fillText(newEntityType?.name ?? 'New Entity Type', canvasWidth - columnWidth, 16, columnWidth);

    for (let i = 0; i < newColumns.length; i++) {
      const column = newColumns[i];

      context.font = '16px monospace';
      if (Object.values(columnMapping).includes(column.name)) {
        context.fillStyle = 'grey';
      } else if (column.dataType.dataType === clickedColumn?.dataType.dataType) {
        context.font = 'bold 16px monospace';
        context.fillStyle = 'green';
      } else {
        context.fillStyle = 'black';
      }
      context.fillText(column.name, canvasWidth - columnWidth, (i + 2) * 24, columnWidth);
      context.strokeRect(canvasWidth - columnWidth - 4, (i + 2) * 24 - 16, columnWidth + 4, 24);
    }

    for (const [oldName, newName] of Object.entries(columnMapping)) {
      const oldIndex = oldColumns.findIndex((c) => c.name === oldName);
      const newIndex = newColumns.findIndex((c) => c.name === newName);

      if (oldIndex >= 0 && newIndex >= 0) {
        context.beginPath();
        context.strokeStyle = 'black';
        context.lineWidth = 2;
        context.moveTo(columnWidth + 4, 24 * oldIndex - 4 + 48);
        context.lineTo(canvasWidth - columnWidth - 4, 24 * newIndex - 4 + 48);
        context.stroke();
      }
    }

    if (clicked) {
      context.beginPath();
      context.strokeStyle = 'red';
      context.lineWidth = 4;
      context.moveTo(columnWidth + 4, 24 * oldColumns.findIndex((c) => c.name === clicked) - 4 + 48);
      context.lineTo(mousePosition.x, mousePosition.y);
      context.stroke();
    }

    context.canvas.onmousedown = (e) => {
      // if old column, set clicked = that
      const x = e.clientX - canvas.getBoundingClientRect().left;
      const y = e.clientY - canvas.getBoundingClientRect().top;
      const index = Math.floor((y - 28) / 24);

      if (x < columnWidth && clicked !== null) {
        delete columnMapping[clicked];
        setColumnMapping({ ...columnMapping });
        setClicked(null);
      } else if (x < columnWidth) {
        if (index >= 0 && index < oldColumns.length) {
          setClicked(oldColumns[index].name);
        }
      } else if (clicked && x > canvasWidth - columnWidth) {
        if (index >= 0 && index < newColumns.length) {
          setColumnMapping((prev) => ({ ...prev, [clicked]: newColumns[index].name }));
          setClicked(null);
        }
      }
    };
  }, [oldEntityType, newEntityType, columnMapping, mousePosition, clicked, showMatched, showOnlyMatchingDataTypes]);

  return (
    <Grid container spacing={2}>
      <Grid item xs={6} sx={{ height: 300, overflow: 'auto' }}>
        <label style={oldEntityType === null ? { color: 'red' } : {}}>Old entity type:</label>
        <CodeMirror
          onChange={(s) => {
            try {
              setOldEntityType(json5.parse(s));
            } catch (e) {
              setOldEntityType(null);
            }
          }}
          extensions={[json(), EditorView.lineWrapping]}
        />
      </Grid>
      <Grid item xs={6} sx={{ height: 300, overflow: 'auto' }}>
        <label style={newEntityType === null ? { color: 'red' } : {}}>New entity type, fully resolved:</label>
        <CodeMirror
          onChange={(s) => {
            try {
              setNewEntityType(json5.parse(s));
            } catch (e) {
              setNewEntityType(null);
            }
          }}
          extensions={[json(), EditorView.lineWrapping]}
        />
      </Grid>
      <Grid item xs={12}>
        <Container>
          <Grid container>
            <Grid item xs={12} sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Button
                color="error"
                variant="outlined"
                onClick={() => {
                  setColumnMapping({});
                }}
              >
                reset
              </Button>
              <FormControlLabel
                control={<Switch checked={showMatched} onChange={(e) => setShowMatched(e.target.checked)} />}
                label="Show matched columns"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={showOnlyMatchingDataTypes}
                    onChange={(e) => setShowOnlyMatchingDataTypes(e.target.checked)}
                  />
                }
                label="Show only matching data types when matching"
              />
            </Grid>
            <Grid item xs={12}>
              <canvas
                ref={canvasRef}
                style={{ width: '100%', cursor: 'pointer' }}
                onMouseMove={(e) => {
                  const rect = (e.target as HTMLCanvasElement).getBoundingClientRect();
                  const x = e.clientX - rect.left;
                  const y = e.clientY - rect.top;

                  setMousePosition({ x, y });
                }}
              />
            </Grid>
            <Grid item xs={12}>
              <CodeMirror value={result} readOnly extensions={[java(), EditorView.lineWrapping]} />
            </Grid>
          </Grid>
        </Container>
      </Grid>
    </Grid>
  );
}
