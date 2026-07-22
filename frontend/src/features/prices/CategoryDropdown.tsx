import { useEffect, useRef, useState } from "react";
import { Check, ChevronDown, ChevronUp } from "lucide-react";
import { categories } from "./priceTypes";

type CategoryDropdownProps = {
  value: string;
  onChange: (category: string) => void;
};

function categoryLabel(category: string) {
  return category.charAt(0) + category.slice(1).toLowerCase();
}

export function CategoryDropdown({ value, onChange }: CategoryDropdownProps) {
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handlePointerDown(event: MouseEvent) {
      if (!dropdownRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  function selectCategory(category: string) {
    onChange(category);
    setOpen(false);
  }

  return (
    <div className="category-select" ref={dropdownRef}>
      <button
        type="button"
        className={open ? "category-trigger open" : "category-trigger"}
        aria-label="Category"
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <span>{categoryLabel(value)}</span>
        {open ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
      </button>

      {open ? (
        <div className="category-menu" role="listbox" aria-label="Product categories">
          <div className="category-menu-heading">Categories</div>
          {categories.map((category) => {
            const selected = category === value;
            return (
              <button
                key={category}
                type="button"
                className={selected ? "category-menu-option selected" : "category-menu-option"}
                role="option"
                aria-selected={selected}
                onClick={() => selectCategory(category)}
              >
                <span>{categoryLabel(category)}</span>
                {selected ? <Check size={15} /> : null}
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}
