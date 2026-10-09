/// <reference types="vite/client" />

declare module 'virtual:design-tokens.css';

declare module 'virtual:design-tokens' {
  const tokens: {
    layers: Record<string, { fill: string; border: string }>;
    values: Record<string, string>;
  };
  export default tokens;
}

declare module '*.svg?raw' {
  const content: string;
  export default content;
}
