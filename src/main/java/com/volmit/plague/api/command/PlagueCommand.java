package com.volmit.plague.api.command;

import com.volmit.iris.util.collection.KList;
import com.volmit.plague.api.Description;
import com.volmit.plague.api.PlagueRegistry;
import com.volmit.plague.api.PlagueSender;
import com.volmit.plague.util.C;

import lombok.Getter;

import java.util.Arrays;

public abstract class PlagueCommand
{
	@Getter
	private final String node;

	@Getter
	private final KList<String> aliases;

	@Getter
	private final KList<PlagueCommand> children;

	@Getter
	private final KList<PlagueCommandFunction> functions;

	@Getter
	private final String description;

	public PlagueCommand()
	{
		String[] nodes = getNodes();
		if(nodes.length < 1)
		{
			throw new RuntimeException("A command must have at least one node");
		}

		this.node = nodes[0];
		aliases = new KList<>();
		children = new KList<>();
		functions = new KList<>();
		this.description = getClass().isAnnotationPresent(Description.class) ? getClass().getDeclaredAnnotation(Description.class).value() : "No Description";

		if(nodes.length > 1)
		{
			aliases.addAll(Arrays.asList(nodes));
		}

		PlagueRegistry.getPlaguedFields(getClass(), PlagueCommand.class).forEach((g) -> children.add((PlagueCommand) PlagueRegistry.init(g.getType())));
		PlagueRegistry.getPlaguedMethods(getClass()).forEach((g) -> functions.add(new PlagueCommandFunction(g, this)));
	}

	public void sendHelp(PlagueSender sender)
	{
		for(PlagueCommandFunction i : getFunctions())
		{
			i.getHelp().tellRawTo(sender.player());
		}
	}

	public static KList<String> enhanceArgs(String[] args)
	{
		KList<String> a = new KList<>();

		if(args.length == 0)
		{
			return a;
		}

		StringBuilder flat = new StringBuilder();
		for(String i : args)
		{
			flat.append(" ").append(i);
		}

		flat = new StringBuilder(flat.substring(1).trim());
		StringBuilder arg = new StringBuilder();
		boolean quoting = false;

		for(int x = 0; x < flat.length(); x++)
		{
			char i = flat.charAt(x);
			char j = x < flat.length() - 1 ? flat.charAt(x + 1) : i;

			if(i == ' ' && !quoting)
			{
				if(!arg.toString().trim().isEmpty())
				{
					a.add(arg.toString().trim());
					arg = new StringBuilder();
				}
			}

			else if(i == '"')
			{
				if(!quoting && (arg.length() == 0))
				{
					quoting = true;
				}

				else if(quoting)
				{
					quoting = false;

					if(j == ' ')
					{
						if(!arg.toString().trim().isEmpty())
						{
							a.add(arg.toString().trim());
							arg = new StringBuilder();
						}
					}
				}
			}

			else
			{
				arg.append(i);
			}
		}

		if(!arg.toString().trim().isEmpty())
		{
			a.add(arg.toString().trim());
		}

		return a;
	}

	public abstract String[] getNodes();

	public boolean hasNode(String node)
	{
		return node.toLowerCase().equals(this.node) || aliases.contains(node.toLowerCase());
	}

	public void handleFunction(PlagueSender sender, KList<String> args)
	{
		KList<String> errors = new KList<>();

		for(PlagueCommandFunction i : getFunctions())
		{
			if(i.isArgumentCountSupported(args.size()))
			{
				String result = i.invoke(this, sender, args);

				if(result != null)
				{
					errors.add("Failed to use " + i.getMethod().toString() + ": " + result);
				}

				else
				{
					return;
				}
			}
		}

		errors.add("Couldn't find a valid function with " + args.size() + " parameters.");

		sender.sendMessage("Failed to process command: " + errors.size() + " error(s)");

		int g = 1;
		for(String i : errors)
		{
			sender.sendMessage("Attempt #" + (g++) + ": " + C.GRAY + i);
		}
	}

	public void handle(PlagueSender sender, KList<String> command)
	{
		if(!command.isEmpty())
		{
			String nodePossibility = command.get(0);

			for(PlagueCommand i : children)
			{
				if(i.node.matches(nodePossibility))
				{
					command.remove(0);
					i.handle(sender, command);
					return;
				}
			}
		}

		handleFunction(sender, command);
	}
}
