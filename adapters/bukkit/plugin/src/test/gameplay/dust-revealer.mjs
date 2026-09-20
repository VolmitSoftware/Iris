export default {
  name: 'iris-dust-revealer',
  description: 'Inspect generated terrain with Dust of Revealing and retry pending saved-biome reads.',
  async run(context) {
    const bot = context.bot;
    const returnPosition = bot.entity.position.clone();
    await context.step('create an Iris terrain world', async () => {
      const response = await context.command('/irislifecycle terrain-create', /NATIVE_TERRAIN seed=|NATIVE_FAILED/, 180000);
      context.expect(response.includes('NATIVE_TERRAIN seed=78264193 columns=16 biome=plains'), response);
    });
    await context.step('enter the generated terrain and receive reveal dust', async () => {
      await context.command(`/execute in iris:native_terrain_probe run tp ${bot.username} 34 101 -46`, /Teleported/, 10000);
      await context.waitForEvent('physicsTick', () => bot.entity.onGround && Math.abs(bot.entity.position.x - 34) < 1, 15000);
      const received = context.waitForEvent('physicsTick', () => bot.inventory.items().some(item => item.name === 'glowstone_dust'), 10000);
      await context.command('/ir o d');
      await received;
      const dust = bot.inventory.items().find(item => item.name === 'glowstone_dust');
      context.expect(dust !== undefined, 'Dust command did not grant glowstone dust');
      await bot.equip(dust, 'hand');
    });
    for (let inspection = 0; inspection < 2; inspection++) {
      await context.step(`inspect the block with reveal dust ${inspection + 1}`, async () => {
        const target = bot.findBlock({ matching: block => block.name === 'lime_wool', maxDistance: 4 });
        context.expect(target !== null, 'Generated terrain block is not within reach');
        for (let attempt = 0; attempt < 10; attempt++) {
          const start = context.report.messages.length;
          const response = context.waitForMessage(/Saved biome information is loading|Click to copy these stats|Object reveal failed/, 10000);
          await bot.activateBlock(target);
          const message = await response;
          if (message.includes('Saved biome information is loading')) {
            await context.sleep(100);
            continue;
          }
          context.expect(message.includes('Click to copy these stats'), message);
          const messages = context.report.messages.slice(start).map(entry => entry.message).join('\n');
          context.expect(/Surface biome: flat/.test(messages), messages);
          context.expect(/Region: flat/.test(messages), messages);
          return;
        }
        context.expect(false, 'Saved biome information remained loading after ten inspections');
      });
    }
    await context.step('leave and unload the inspected world', async () => {
      await context.command(`/execute in minecraft:overworld run tp ${bot.username} ${returnPosition.x} ${returnPosition.y} ${returnPosition.z}`, /Teleported/, 10000);
      const response = await context.command('/irislifecycle terrain-unload', /NATIVE_TERRAIN_UNLOADED|NATIVE_FAILED/, 90000);
      context.expect(response.includes('NATIVE_TERRAIN_UNLOADED true'), response);
    });
  }
};
