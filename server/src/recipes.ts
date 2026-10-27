/** User-created recipes: saved meals and meal templates. */

export interface RecipeItem {
  foodId: string;
  qty: number; // servings
  name?: string; // for custom items
  kcal?: number;
  protein_g?: number;
  carbs_g?: number;
  fat_g?: number;
}

export interface Recipe {
  id: string;
  userId: string;
  name: string;
  description?: string;
  items: RecipeItem[];
  servings: number; // how many people this recipe feeds
  prepTime?: number; // minutes
  cookTime?: number;
  tags: string[]; // "breakfast", "vegetarian", "quick", etc
  createdAt: number;
  updatedAt: number;
}

export interface RecipeListItem {
  id: string;
  name: string;
  kcal: number;
  servings: number;
  tags: string[];
}

export interface ShoppingListItem {
  foodId?: string;
  name: string;
  unit: string; // "g", "ml", "pieces", "cups"
  quantity: number;
  checked: boolean;
}

export interface ShoppingList {
  recipes: string[]; // recipe ids used to generate
  items: ShoppingListItem[];
  generatedAt: number;
}

/**
 * Calculate nutrition for a recipe from its items.
 * Assumes items have either foodId (lookup in library) or explicit nutrition numbers.
 */
export function recipeNutrition(recipe: Recipe, library: Map<string, any>): {
  kcal: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  perServing: { kcal: number; protein_g: number; carbs_g: number; fat_g: number };
} {
  let kcal = 0;
  let protein_g = 0;
  let carbs_g = 0;
  let fat_g = 0;

  for (const item of recipe.items) {
    const food = library.get(item.foodId);
    if (food) {
      kcal += food.kcal * item.qty;
      protein_g += (food.protein_g || 0) * item.qty;
      carbs_g += (food.carbs_g || 0) * item.qty;
      fat_g += (food.fat_g || 0) * item.qty;
    } else if (item.kcal !== undefined) {
      // Custom item with explicit nutrition
      kcal += item.kcal * item.qty;
      protein_g += (item.protein_g || 0) * item.qty;
      carbs_g += (item.carbs_g || 0) * item.qty;
      fat_g += (item.fat_g || 0) * item.qty;
    }
  }

  const servings = Math.max(1, recipe.servings);
  return {
    kcal,
    protein_g,
    carbs_g,
    fat_g,
    perServing: { kcal: kcal / servings, protein_g: protein_g / servings, carbs_g: carbs_g / servings, fat_g: fat_g / servings },
  };
}

/**
 * Generate a shopping list from one or more recipes.
 * Groups items by food and sums quantities.
 */
export function generateShoppingList(recipeIds: string[], recipes: Map<string, Recipe>): ShoppingListItem[] {
  const items = new Map<string, { unit: string; qty: number }>();

  for (const recipeId of recipeIds) {
    const recipe = recipes.get(recipeId);
    if (!recipe) continue;

    for (const item of recipe.items) {
      const key = item.foodId;
      const current = items.get(key) || { unit: "g", qty: 0 };
      current.qty += item.qty;
      items.set(key, current);
    }
  }

  return Array.from(items.entries()).map(([foodId, { unit, qty }]) => ({
    foodId,
    name: "", // will be filled in by UI from library
    unit,
    quantity: qty,
    checked: false,
  }));
}

/**
 * Estimate ingredient quantities for a recipe from its nutrition targets.
 * Used when user doesn't know exact amounts but knows total recipe nutrition.
 */
export function estimateFromNutrition(recipe: Recipe, targetKcal: number): RecipeItem[] {
  // This would be used with Claude to estimate ingredient amounts
  // based on the meal description and target nutrition
  return recipe.items;
}
