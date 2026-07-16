import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

/**
 * Light/dark theme, persisted in localStorage and applied via the
 * {@code data-theme} attribute on <html>. CSS variables in styles.scss react
 * to that attribute; components that draw on a canvas (Chart.js) read the
 * {@link theme} signal to recolour themselves.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.initial());

  constructor() {
    this.apply(this.theme());
  }

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    localStorage.setItem('theme', next);
    this.apply(next);
  }

  /** Axis/legend text colour for charts under the current theme. */
  chartText(): string {
    return this.theme() === 'dark' ? '#A9AEB8' : '#6B7280';
  }

  /** Grid line colour for charts under the current theme. */
  chartGrid(): string {
    return this.theme() === 'dark' ? 'rgba(255,255,255,.07)' : 'rgba(31,36,48,.07)';
  }

  private apply(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }

  private initial(): Theme {
    const saved = localStorage.getItem('theme');
    if (saved === 'dark' || saved === 'light') return saved;
    return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
