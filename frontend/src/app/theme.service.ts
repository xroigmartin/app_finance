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

  /** Primary series colour (--accent) for charts under the current theme. */
  chartAccent(): string {
    return this.theme() === 'dark' ? '#5B8DEF' : '#2563EB';
  }

  /** Soft accent fill (--accent-soft) for area charts under the current theme. */
  chartAccentSoft(): string {
    return this.theme() === 'dark' ? 'rgba(91,141,239,.16)' : 'rgba(37,99,235,.10)';
  }

  /** Positive/income colour (--pos) for charts under the current theme. */
  chartPos(): string {
    return this.theme() === 'dark' ? '#3DC78A' : '#16A06B';
  }

  /** Negative/expense colour (--neg) for charts under the current theme. */
  chartNeg(): string {
    return this.theme() === 'dark' ? '#F06A5E' : '#E0453A';
  }

  /** Warning colour (--warn) for charts under the current theme. */
  chartWarn(): string {
    return this.theme() === 'dark' ? '#F0B85A' : '#E8A33D';
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
