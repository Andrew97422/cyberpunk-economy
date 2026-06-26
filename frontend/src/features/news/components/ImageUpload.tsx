import { ChangeEvent, useRef, useState } from 'react';
import { extractError } from '../../../shared/utils';
import { uploadNewsMedia } from '../api';

const ACCEPT = 'image/png,image/jpeg,image/gif,image/webp';

type SingleProps = {
  label: string;
  multiple?: false;
  value: string;
  onChange: (value: string) => void;
};

type MultiProps = {
  label: string;
  multiple: true;
  value: string[];
  onChange: (value: string[]) => void;
};

type ImageUploadProps = SingleProps | MultiProps;

export default function ImageUpload(props: ImageUploadProps) {
  const { label } = props;
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const openPicker = () => inputRef.current?.click();

  const handleFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    // reset the input so re-selecting the same file fires onChange again
    e.target.value = '';
    if (!file) return;
    setUploading(true);
    setError('');
    try {
      const url = await uploadNewsMedia(file);
      if (props.multiple) {
        props.onChange([...props.value, url]);
      } else {
        props.onChange(url);
      }
    } catch (err) {
      setError(extractError(err));
    } finally {
      setUploading(false);
    }
  };

  const renderSingle = (value: string, onChange: (v: string) => void) => (
    <div className="image-upload-single">
      {value ? (
        <div className="image-upload-preview">
          <img src={value} alt="" />
          <button
            type="button"
            className="image-upload-remove"
            onClick={() => onChange('')}
            title="Удалить изображение"
            aria-label="Удалить изображение"
          >
            ×
          </button>
        </div>
      ) : (
        <button
          type="button"
          className="image-upload-dropzone"
          onClick={openPicker}
          disabled={uploading}
        >
          {uploading ? 'Загрузка…' : '＋ Выбрать обложку'}
        </button>
      )}
      {value && (
        <button type="button" className="btn btn-sm btn-secondary" onClick={openPicker} disabled={uploading}>
          {uploading ? 'Загрузка…' : 'Заменить'}
        </button>
      )}
    </div>
  );

  const renderMulti = (value: string[], onChange: (v: string[]) => void) => (
    <div className="image-upload-multi">
      <button
        type="button"
        className="image-upload-dropzone"
        onClick={openPicker}
        disabled={uploading}
      >
        {uploading ? 'Загрузка…' : '＋ Добавить изображение'}
      </button>
      {value.length > 0 && (
        <div className="image-upload-grid">
          {value.map((url, i) => (
            <div className="image-upload-thumb" key={`${url}-${i}`}>
              <img src={url} alt="" />
              <button
                type="button"
                className="image-upload-remove"
                onClick={() => onChange(value.filter((_, idx) => idx !== i))}
                title="Удалить"
                aria-label="Удалить"
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );

  return (
    <div className="image-upload">
      <span className="image-upload-label">{label}</span>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        onChange={handleFile}
        disabled={uploading}
        style={{ display: 'none' }}
      />
      {props.multiple
        ? renderMulti(props.value, props.onChange)
        : renderSingle(props.value, props.onChange)}
      {error && <div className="image-upload-error">{error}</div>}
    </div>
  );
}
