import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ApiService } from '../api.service';
import { FlexImportResult } from '../models';
import { FlexImportDialog } from './flex-import-dialog';

describe('FlexImportDialog', () => {
  let api: { importFlexReport: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  const okResult: FlexImportResult = { imported: 3, duplicated: 1, errors: [], warnings: [] };

  function create(): FlexImportDialog {
    api = { importFlexReport: vi.fn().mockReturnValue(of(okResult)) };
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiService, useValue: api },
        { provide: Router, useValue: router },
      ],
    });
    const dialog = TestBed.createComponent(FlexImportDialog).componentInstance;
    dialog.portfolioId = 1;
    return dialog;
  }

  function fakeInput(file: File | null): HTMLInputElement {
    return { files: file ? [file] : [], value: 'x' } as unknown as HTMLInputElement;
  }

  it('open resetea el estado del diálogo', () => {
    const dialog = create();
    dialog.file = new File(['x'], 'x.xml');
    dialog.result = okResult;
    dialog.error = 'algo';
    dialog.open();
    expect(dialog.visible).toBe(true);
    expect(dialog.file).toBeNull();
    expect(dialog.result).toBeNull();
    expect(dialog.error).toBe('');
  });

  it('close oculta el diálogo', () => {
    const dialog = create();
    dialog.visible = true;
    dialog.close();
    expect(dialog.visible).toBe(false);
  });

  it('onFile toma el fichero y limpia el input', () => {
    const dialog = create();
    const file = new File(['<xml/>'], 'flex.xml');
    const input = fakeInput(file);
    dialog.onFile(input);
    expect(dialog.file).toBe(file);
    expect(input.value).toBe('');
  });

  describe('doImport', () => {
    it('sin fichero, no hace nada', () => {
      const dialog = create();
      dialog.doImport();
      expect(api.importFlexReport).not.toHaveBeenCalled();
    });

    it('importa a la cartera indicada', () => {
      const dialog = create();
      const file = new File(['<xml/>'], 'flex.xml');
      dialog.file = file;
      dialog.doImport();
      expect(api.importFlexReport).toHaveBeenCalledWith(1, file);
    });

    it('en éxito con operaciones importadas, emite done', () => {
      const dialog = create();
      const onDone = vi.fn();
      dialog.done.subscribe(onDone);
      dialog.file = new File(['<xml/>'], 'flex.xml');
      dialog.doImport();
      expect(dialog.result).toEqual(okResult);
      expect(dialog.file).toBeNull();
      expect(onDone).toHaveBeenCalled();
    });

    it('en éxito sin operaciones importadas, también emite done (RF-12: el intento queda registrado igualmente)', () => {
      const dialog = create();
      api.importFlexReport.mockReturnValue(of({ imported: 0, duplicated: 0, errors: [], warnings: [] }));
      const onDone = vi.fn();
      dialog.done.subscribe(onDone);
      dialog.file = new File(['<xml/>'], 'flex.xml');
      dialog.doImport();
      expect(onDone).toHaveBeenCalled();
    });

    it('en error, usa el detail/message de la API o el mensaje genérico', () => {
      const dialog = create();
      api.importFlexReport.mockReturnValue(throwError(() => ({ error: { message: 'msg' } })));
      dialog.file = new File(['<xml/>'], 'flex.xml');
      dialog.doImport();
      expect(dialog.error).toBe('msg');

      api.importFlexReport.mockReturnValue(throwError(() => ({ error: null })));
      dialog.doImport();
      expect(dialog.error).toBe('Error al importar el informe.');
    });
  });

  describe('goToImportHistory', () => {
    it('cierra el diálogo y navega a la pestaña Importaciones de Operaciones', () => {
      const dialog = create();
      dialog.visible = true;
      dialog.goToImportHistory();
      expect(dialog.visible).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(
        ['/investments/operations'], { queryParams: { tab: 'importaciones' } });
    });
  });
});
