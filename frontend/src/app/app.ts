import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ThemeService } from './theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected theme = inject(ThemeService);
  collapsed = localStorage.getItem('sidebar-collapsed') === '1';

  toggle(): void {
    this.collapsed = !this.collapsed;
    localStorage.setItem('sidebar-collapsed', this.collapsed ? '1' : '0');
  }

  toggleTheme(): void {
    this.theme.toggle();
  }
}
