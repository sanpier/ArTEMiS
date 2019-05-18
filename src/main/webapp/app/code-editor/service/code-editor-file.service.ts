import { DeleteFileChange, FileChange, RenameFileChange } from 'app/entities/ace-editor/file-change.model';
import { compose, filter, fromPairs, map, tap, toPairs } from 'lodash/fp';
import { isEmpty as _isEmpty } from 'lodash';

export class CodeEditorFileService {
    updateFileReferences = (refs: { [fileName: string]: any }, fileChange: FileChange) => {
        if (fileChange instanceof RenameFileChange) {
            const testRegex = new RegExp(`^${fileChange.oldFileName}($|/.*)`);
            const replaceRegex = new RegExp(`^${fileChange.oldFileName}`);
            return compose(
                fromPairs,
                map(([fileName, refContent]) => [testRegex.test(fileName) ? fileName.replace(replaceRegex, fileChange.newFileName) : fileName, refContent]),
                filter(entry => !_isEmpty(entry)),
                toPairs,
            )(refs);
        } else if (fileChange instanceof DeleteFileChange) {
            const testRegex = new RegExp(`^${fileChange.fileName}($|/.*)`);
            return compose(
                fromPairs,
                filter(([fileName]) => !testRegex.test(fileName)),
                filter(entry => !_isEmpty(entry)),
                toPairs,
            )(refs);
        } else {
            return refs;
        }
    };
    updateFileReference = (fileName: string, fileChange: FileChange) => {
        if (fileChange instanceof RenameFileChange) {
            const testRegex = new RegExp(`^${fileChange.oldFileName}($|/.*)`);
            const replaceRegex = new RegExp(`^${fileChange.oldFileName}`);
            return testRegex.test(fileName) ? fileName.replace(replaceRegex, fileChange.newFileName) : fileName;
        } else if (fileChange instanceof DeleteFileChange) {
            const testRegex = new RegExp(`^${fileChange.fileName}($|/.*)`);
            return testRegex.test(fileName) ? null : fileName;
        } else {
            return fileName;
        }
    };
}
