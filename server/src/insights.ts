import Anthropic from "@anthropic-ai/sdk";
import { LIMITS } from "./index";
import type { Spend } from "./index";

/** Weekly/monthly nutrition summary from the app (locally computed). */
export interface NutritionSummary {
  period: "week" | "month"; // "week" or "month"
  startDate: string; // yyyy-MM-dd
  endDate: string;
  daysCount: number; // number of days with entries
  totalDays: number; // 7 or 30
  totalKcal: number;
  totalProtein: number;
  totalCarbs: number;
  totalFat: number;
  avgKcal: number;
  avgProtein: number;
  avgCarbs: number;
  avgFat: number;
  mealsPerDay: number;
  courseBreakdown: Record<string, number>; // e.g. { "main": 45%, "soup": 20% }
}

/** AI-generated coaching tips. */
export interface InsightResult {
  title: string;
  patterns: string[]; // 2-3 observations about eating patterns
  strengths: string[]; // 2 things done well
  improvements: string[]; // 2-3 actionable suggestions
  substitutionTip?: string; // One specific food swap suggestion
}

export async function insights(
  client: InstanceType<typeof Anthropic>,
  body: any,
  spend: Spend,
  lang: string,
): Promise<InsightResult> {
  const summary: NutritionSummary = body?.summary;
  if (!summary || !summary.period || typeof summary.avgKcal !== "number") {
    throw new Error("Invalid nutrition summary");
  }

  const prompt = buildPrompt(summary, lang);
  const started = Date.now();

  const message = await client.messages.create({
    model: "claude-haiku-5-5",
    max_tokens: 600,
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
  return parseInsights(text, summary, lang);
}

function buildPrompt(summary: NutritionSummary, lang: string): string {
  const period = summary.period === "week" ? (lang === "en" ? "week" : "tuần") : lang === "en" ? "month" : "tháng";
  const avgCalMsg =
    lang === "en"
      ? `Average ${Math.round(summary.avgKcal)} kcal/day`
      : `Trung bình ${Math.round(summary.avgKcal)} kcal/ngày`;

  return `You are a friendly nutrition coach. Analyze this ${period} of eating data and provide brief, actionable insights in ${lang === "en" ? "English" : "Vietnamese"}.

${period === "week" ? lang === "en" ? "**7-Day Nutrition Summary**" : "**Tóm tắt 7 ngày**" : lang === "en" ? "**30-Day Nutrition Summary**" : "**Tóm tắt 30 ngày**"}
- Days with meals: ${summary.daysCount}/${summary.totalDays}
- ${avgCalMsg}
- Macro average: ${Math.round(summary.avgProtein)}g protein, ${Math.round(summary.avgCarbs)}g carbs, ${Math.round(summary.avgFat)}g fat
- Meals/day: ${summary.mealsPerDay.toFixed(1)}
- Top meals: ${Object.entries(summary.courseBreakdown)
    .sort(([, a], [, b]) => (b as number) - (a as number))
    .slice(0, 2)
    .map(([meal, pct]) => `${meal} (${Math.round((pct as number) * 100)}%)`)
    .join(", ")}

Provide EXACTLY this JSON structure (no markdown, just JSON):
{
  "title": "${lang === "en" ? "Your Weekly Check-In" : "Nhận xét tuần này"}",
  "patterns": ["observation 1", "observation 2"],
  "strengths": ["strength 1", "strength 2"],
  "improvements": ["tip 1", "tip 2"],
  "substitutionTip": "One specific food swap (optional)"
}

Be brief (2-5 words per item). Be specific to the data shown, not generic.`;
}

function parseInsights(text: string, summary: NutritionSummary, lang: string): InsightResult {
  try {
    const result = JSON.parse(text);
    return {
      title: result.title || (lang === "en" ? "Your Weekly Review" : "Nhận xét tuần này"),
      patterns: Array.isArray(result.patterns) ? result.patterns.slice(0, 3) : [],
      strengths: Array.isArray(result.strengths) ? result.strengths.slice(0, 2) : [],
      improvements: Array.isArray(result.improvements) ? result.improvements.slice(0, 3) : [],
      substitutionTip: typeof result.substitutionTip === "string" ? result.substitutionTip : undefined,
    };
  } catch {
    // Fallback if JSON parsing fails
    return {
      title: lang === "en" ? "Your Weekly Review" : "Nhận xét tuần này",
      patterns: [
        lang === "en" ? `Average ${Math.round(summary.avgKcal)} kcal/day` : `Trung bình ${Math.round(summary.avgKcal)} kcal/ngày`,
      ],
      strengths: [
        lang === "en" ? "Consistent meal tracking" : "Theo dõi bữa ăn đều đặn",
      ],
      improvements: [
        lang === "en" ? "Increase protein intake" : "Tăng lượng protein",
      ],
    };
  }
}
