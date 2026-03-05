type ThemeSwitchProps = {
  checked: boolean;
  onChange: (checked: boolean) => void;
};

export default function ThemeSwitch({ checked, onChange }: ThemeSwitchProps) {
  return (
    <label className="theme-switch">
      <input
        type="checkbox"
        role="switch"
        aria-label="Dark mode"
        aria-checked={checked}
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            onChange(!checked);
          }
        }}
      />
      <span className="slider" aria-hidden="true" />
    </label>
  );
}
