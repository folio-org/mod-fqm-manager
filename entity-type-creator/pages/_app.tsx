import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';
import { AppProps } from 'next/app';

const App = (props: AppProps) => {
  const { Component, pageProps } = props;

  return (
    <ThemeProvider
      theme={createTheme({
        components: {
          MuiAccordionSummary: {
            styleOverrides: {
              root: {
                backgroundColor: '#eee',
                fontWeight: 'bolder',
              },
            },
          },
        },
      })}
    >
      <CssBaseline />
      <Component {...pageProps} />
    </ThemeProvider>
  );
};

export default App;
