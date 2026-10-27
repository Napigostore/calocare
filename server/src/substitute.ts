import Anthropic from "@anthropic-ai/sdk";
import type { Spend } from "./index";

export interface DetectedFood {
  name: string;
  kcal: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  fiber_g?: number;
  tags: string[];
}

export interface Substitution {
  name: string;
  kcal: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  reason: string; // why this is better
  benefits: string[]; // 1-2 specific benefits
}

export async function substitute(
  client: InstanceType<typeof Anthropic>,
  body: any,
  spend: Spend,
  lang: string,
): Promise<Substitution | null> {
  const food: DetectedFood = body?.food;
  if (!food || typeof food.name !== "string") {
    throw new Error("Invalid food data");
  }

  const prompt = buildSubstitutePrompt(food, lang);
  const started = Date.now();

  const message = await client.messages.create({
    model: "claude-haiku-5-5",
    max_tokens: 300,
    messages: [
      {
        role: "user",
        content: prompt,
      },
    ],
  });

  spend.ai_ms = (spend.ai_ms ?? 0) + Date.now() - started;
  spend.input_tokens += message.usage.input_tokens;
  spend.output_tokens += message.usage.output_tokens;
  spend.cache_read_tokens += message.usage.cache_read_input_tokens ?? 0;
  spend.cache_write_tokens += message.usage.cache_creation_input_tokens ?? 0;

  const text =
    message.content[0]?.type === "text" ? message.content[0].text : "";
  return parseSubstitution(text, lang);
}

function buildSubstitutePrompt(food: DetectedFood, lang: string): string {
  return `You are a nutrition advisor. Suggest ONE healthier alternative to: "${food.name}" (${food.kcal} kcal, ${food.protein_g}g protein, ${food.carbs_g}g carbs, ${food.fat_g}g fat).

Be specific about a REAL food, not a generic category. Suggest a swap that:
- Has similar or lower calories
- Improves a macro weakness (e.g., if low protein, pick high protein)
- Is tasty and realistic for the user

Respond ONLY as valid JSON (no markdown), no extra text:
{
  "name": "specific food name",
  "kcal": 250,
  "protein_g": 15,
  "carbs_g": 30,
  "fat_g": 8,
  "reason": "${lang === "en" ? "Why this swap" : "Tại sao thay này"} (one sentence, ${lang === "en" ? "in English" : "tiếng Việt"})",
  "benefits": ["benefit 1", "benefit 2"]
}`;
}

function parseSubstitution(text: string, lang: string): Substitution | null {
  try {
    // Try to find JSON in the response
    const jsonMatch = text.match(/\{[\s\S]*\}/);
    if (!jsonMatch) return null;

    const parsed = JSON.parse(jsonMatch[0]);
    return {
      name: parsed.name || "",
      kcal: parseInt(parsed.kcal) || 0,
      protein_g: parseFloat(parsed.protein_g) || 0,
      carbs_g: parseFloat(parsed.carbs_g) || 0,
      fat_g: parseFloat(parsed.fat_g) || 0,
      reason:
        parsed.reason ||
        (lang === "en"
          ? "Healthier alternative"
          : "Lựa chọn lành mạnh hơn"),
      benefits: Array.isArray(parsed.benefits)
        ? parsed.benefits.slice(0, 2)
        : [],
    };
  } catch {
    return null;
  }
}
