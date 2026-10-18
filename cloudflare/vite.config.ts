import { cloudflare } from '@cloudflare/vite-plugin';
import commonjs from '@rollup/plugin-commonjs';
import { defineConfig } from 'vite';

export default defineConfig({ plugins: [
  commonjs({ include: [/[/\\]functions[/\\](invitation-policy|entry-deletion-policy)\.js$/] }),
  cloudflare(),
] });
