import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

function mockMatchMedia(matches: boolean): void {
  vi.spyOn(window, 'matchMedia').mockReturnValue({
    matches,
    media: '',
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  } as MediaQueryList);
}

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    mockMatchMedia(false);
  });

  function create(): ThemeService {
    TestBed.configureTestingModule({});
    return TestBed.inject(ThemeService);
  }

  it('sin nada guardado y el SO en claro, arranca en claro', () => {
    const service = create();
    expect(service.theme()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('sin nada guardado y el SO en oscuro, arranca en oscuro', () => {
    mockMatchMedia(true);
    const service = create();
    expect(service.theme()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('respeta lo guardado en localStorage por encima de la preferencia del SO', () => {
    localStorage.setItem('theme', 'dark');
    mockMatchMedia(false);
    const service = create();
    expect(service.theme()).toBe('dark');
  });

  it('ignora un valor inválido guardado y cae a la preferencia del SO', () => {
    localStorage.setItem('theme', 'purple');
    mockMatchMedia(true);
    const service = create();
    expect(service.theme()).toBe('dark');
  });

  it('toggle() alterna el tema, lo persiste y actualiza el atributo del documento', () => {
    const service = create();
    expect(service.theme()).toBe('light');

    service.toggle();
    expect(service.theme()).toBe('dark');
    expect(localStorage.getItem('theme')).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');

    service.toggle();
    expect(service.theme()).toBe('light');
    expect(localStorage.getItem('theme')).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  describe('colores de gráfico', () => {
    it('en tema claro', () => {
      const service = create();
      expect(service.chartText()).toBe('#6B7280');
      expect(service.chartGrid()).toBe('rgba(31,36,48,.07)');
      expect(service.chartAccent()).toBe('#2563EB');
      expect(service.chartAccentSoft()).toBe('rgba(37,99,235,.10)');
      expect(service.chartPos()).toBe('#16A06B');
      expect(service.chartNeg()).toBe('#E0453A');
      expect(service.chartWarn()).toBe('#E8A33D');
    });

    it('en tema oscuro', () => {
      mockMatchMedia(true);
      const service = create();
      expect(service.chartText()).toBe('#A9AEB8');
      expect(service.chartGrid()).toBe('rgba(255,255,255,.07)');
      expect(service.chartAccent()).toBe('#5B8DEF');
      expect(service.chartAccentSoft()).toBe('rgba(91,141,239,.16)');
      expect(service.chartPos()).toBe('#3DC78A');
      expect(service.chartNeg()).toBe('#F06A5E');
      expect(service.chartWarn()).toBe('#F0B85A');
    });
  });
});
