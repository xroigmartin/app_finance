import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { ThemeService } from './theme.service';

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('arranca con el menú desplegado si no hay nada guardado', () => {
    const app = TestBed.createComponent(App).componentInstance;
    expect(app.collapsed).toBe(false);
  });

  it('arranca colapsado si localStorage lo dice', () => {
    localStorage.setItem('sidebar-collapsed', '1');
    const app = TestBed.createComponent(App).componentInstance;
    expect(app.collapsed).toBe(true);
  });

  it('toggle() alterna el estado y lo persiste', () => {
    const app = TestBed.createComponent(App).componentInstance;

    app.toggle();
    expect(app.collapsed).toBe(true);
    expect(localStorage.getItem('sidebar-collapsed')).toBe('1');

    app.toggle();
    expect(app.collapsed).toBe(false);
    expect(localStorage.getItem('sidebar-collapsed')).toBe('0');
  });

  it('toggleTheme() delega en ThemeService', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const theme = TestBed.inject(ThemeService);
    const before = theme.theme();

    app.toggleTheme();

    expect(theme.theme()).not.toBe(before);
  });
});
