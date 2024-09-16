// https://github.com/bibinantony1998/react-table-column-resizer/pull/3
declare module 'react-table-column-resizer' {
  export interface Props {
    minWidth?: number;
    maxWidth?: number | null;
    id: number;
    resizeStart?: () => void;
    resizeEnd?: (width: number) => void;
    className?: string;
    disabled?: boolean;
    defaultWidth?: number;
    rowSpan?: number;
    colSpan?: number;
  }

  export default function ColumnResizer(props: Props): JSX.Element;
}
