package common;

//-----------------------------------------------------
// for easy extension of compiler
// [TODO] can be improved.
//-----------------------------------------------------
public class gm_builtin_desc_t
{
	public String def_string; //source:type:name:return_type:num_args:arg_type0:arg_type1: ...
	public gm_method_id_t method_id;
	public String extra_info; //key:value,key:value,...
}