import { ResultRowPretty } from '@/scripts/dump-entity-type-information';
import { Mapping } from '@/scripts/get-column-mapping';
import { Grid } from '@mui/material';
import { parse } from 'papaparse';
import { useState } from 'react';
import Dropzone, { Accept } from 'react-dropzone';

function UploadTarget({
  label,
  lgWidth,
  format,
  onSuccess,
}: Readonly<{
  label: string;
  lgWidth: number;
  format: Accept;
  onSuccess: (data: string) => void;
}>) {
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  return (
    <Dropzone
      onDrop={(acceptedFiles, rejections) => {
        if (rejections.length) {
          return setError(
            rejections
              .flatMap((r) => r.errors)
              .map((e) => e.message)
              .join(', '),
          );
        }

        setError(null);

        const file = acceptedFiles[0];

        const reader = new FileReader();

        reader.onabort = () => setError('file reading was aborted');
        reader.onerror = () => setError('file reading has failed');
        reader.onload = () => {
          setSuccess(file.name);
          onSuccess(reader.result as string);
        };
        reader.readAsText(file);
      }}
      accept={format}
      maxFiles={1}
    >
      {({ getRootProps, getInputProps, isDragActive }) => (
        <Grid
          item
          xs={12}
          md={4}
          lg={lgWidth}
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'space-around',
            border: '1px solid black',
            textAlign: 'center',
            '& > p': { m: 0 },
          }}
          {...getRootProps()}
        >
          <input {...getInputProps()} />
          {isDragActive ? (
            <p>drop the file here ...</p>
          ) : success ? (
            <p style={{ color: 'green' }}>loaded {success}</p>
          ) : (
            <p>drag the {label} here, or click to select</p>
          )}
          {error && <p style={{ color: 'red' }}>{error}</p>}
        </Grid>
      )}
    </Dropzone>
  );
}

export default function Uploader({
  setOldFields,
  setNewFields,
  setMapping,
}: Readonly<{
  setOldFields: (v: ResultRowPretty[]) => void;
  setNewFields: (v: ResultRowPretty[]) => void;
  setMapping: (v: Mapping[]) => void;
}>) {
  return (
    <Grid container>
      <UploadTarget
        format={{ 'text/csv': ['.csv'] }}
        label="original fields"
        lgWidth={5}
        onSuccess={(data) => setOldFields(parse<ResultRowPretty>(data, { header: true }).data)}
      />
      <UploadTarget
        format={{ 'application/json': ['.json'] }}
        label="mappings"
        lgWidth={2}
        onSuccess={(data) => setMapping(JSON.parse(data))}
      />
      <UploadTarget
        format={{ 'text/csv': ['.csv'] }}
        label="current fields"
        lgWidth={5}
        onSuccess={(data) => setNewFields(parse<ResultRowPretty>(data, { header: true }).data)}
      />
    </Grid>
  );
}
